package com.zivxio.gitscreen.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import com.zivxio.gitscreen.GitScreenApp
import com.zivxio.gitscreen.data.repository.DetectionResult
import com.zivxio.gitscreen.data.repository.ScreenshotRepository
import com.zivxio.gitscreen.detection.ForegroundAppDetector
import com.zivxio.gitscreen.detection.ScreenshotOrganizer
import com.zivxio.gitscreen.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Long-running foreground service that watches the MediaStore images collection
 * for new screenshots and organizes them automatically. Runs as a foreground
 * service (with a minimal-priority notification) because background
 * ContentObservers are unreliable once the app leaves the recent-apps list on
 * modern Android versions.
 *
 * A single screenshot write typically fires the ContentObserver several times
 * (pending insert, metadata scan, IS_PENDING cleared, thumbnail generation...).
 * Two things keep that from turning into duplicate organized copies:
 *  1. [scanMutex] serializes every scan so overlapping onChange callbacks never
 *     process the same MediaStore row concurrently.
 *  2. [scheduleDebouncedScan] coalesces a burst of onChange calls into a single
 *     scan, without ever cancelling a scan that's already running (a cancel-based
 *     version of this previously caused every screenshot to fall back to the
 *     manual prompt — see its doc comment for details).
 * [ScreenshotRepository.findByOriginalUri] is the last line of defense — even if
 * both of those somehow miss, a screenshot is never filed twice under the same
 * original URI.
 */
class ScreenshotWatcherService : Service() {

    // SupervisorJob is essential here: a plain Job() would cancel the whole scope
    // (and silently stop all future scans, forever, until the service restarts)
    // the moment any single scan throws an unhandled exception. SupervisorJob lets
    // one failed child coroutine fail on its own without taking down its siblings.
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val scanMutex = Mutex()

    private lateinit var handlerThread: HandlerThread
    private lateinit var observer: ScreenshotContentObserver

    private lateinit var detector: ForegroundAppDetector
    private lateinit var organizer: ScreenshotOrganizer
    private lateinit var prefs: android.content.SharedPreferences

    private var lastProcessedRowId: Long = -1

    // Guards scheduleDebouncedScan(). Unlike the old cancel-and-relaunch approach,
    // this never interrupts a scan that's already running — it only prevents
    // scheduling a *second* one while one is pending or in flight. Any onChange
    // that arrives mid-scan sets retriggerRequested instead of being lost, so it
    // still gets picked up promptly right after the current scan finishes.
    private val scanScheduled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val retriggerRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(applicationContext)
        organizer = ScreenshotOrganizer(applicationContext)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        handlerThread = HandlerThread("gitscreen-observer").apply { start() }
        observer = ScreenshotContentObserver(Handler(handlerThread.looper)) {
            scheduleDebouncedScan()
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        scope.launch {
            // Persisted across restarts so a service restart (process death, reboot)
            // doesn't silently skip screenshots taken while it was down. Only fall
            // back to "start from now" on the very first run ever.
            lastProcessedRowId = prefs.getLong(PREF_LAST_ROW_ID, -1L)
            if (lastProcessedRowId == -1L) {
                lastProcessedRowId = latestRowId() ?: -1L
                persistLastProcessedRowId()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.WATCH_NOTIFICATION_ID, NotificationHelper.buildWatchNotification(this))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
        handlerThread.quitSafely()
        job.cancel()
    }

    /**
     * Coalesces a burst of onChange events into a single scan, ~600ms after the
     * first one in the burst — without ever cancelling a scan that's already
     * running. (An earlier version reused the same Job for "waiting" and "running
     * the scan", so a later onChange in the same burst would cancel the scan
     * mid-flight via debounceJob?.cancel() — that's what was making every
     * screenshot fall back to the manual prompt.)
     */
    private fun scheduleDebouncedScan() {
        if (!scanScheduled.compareAndSet(false, true)) {
            // A scan is already pending or running — just flag that another one
            // should run right after, instead of dropping this trigger entirely.
            retriggerRequested.set(true)
            return
        }
        scope.launch {
            try {
                delay(DEBOUNCE_MS)
                checkForNewScreenshots()
            } finally {
                scanScheduled.set(false)
                if (retriggerRequested.getAndSet(false)) {
                    scheduleDebouncedScan()
                }
            }
        }
    }

    private fun latestRowId(): Long? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            }
        }
        return null
    }

    private fun persistLastProcessedRowId() {
        prefs.edit().putLong(PREF_LAST_ROW_ID, lastProcessedRowId).apply()
    }

    /**
     * Polls MediaStore for rows newer than [lastProcessedRowId], filters down to
     * likely-screenshot images, and organizes each one. Guarded by [scanMutex] so
     * only one scan ever runs at a time — this is what stops duplicate copies.
     */
    private suspend fun checkForNewScreenshots() {
        scanMutex.withLock {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media._ID} > ?"
            val selectionArgs = arrayOf(lastProcessedRowId.toString())

            val newRows = mutableListOf<Triple<Long, Uri, Long>>()
            var highestSeenId = lastProcessedRowId

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media._ID} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                    highestSeenId = maxOf(highestSeenId, id)

                    if (path.contains("GitScreen")) {
                        // Our own organized copy — ignore, avoid infinite loop.
                        continue
                    }
                    if (!looksLikeScreenshot(name, path)) {
                        continue
                    }

                    val dateAddedSec = cursor.getLong(dateCol)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    newRows += Triple(id, uri, dateAddedSec * 1000L)
                }
            }

            // Claim the whole batch immediately, before any (slower) detection/copy
            // work runs, so a scan triggered again mid-way through never sees these
            // rows as "new" a second time.
            lastProcessedRowId = highestSeenId
            persistLastProcessedRowId()

            val app = applicationContext as GitScreenApp
            for ((_, uri, takenAtMs) in newRows) {
                try {
                    processScreenshot(app.repository, uri, takenAtMs)
                } catch (e: Exception) {
                    // Never let one bad screenshot abort the rest of the batch, and
                    // never let it escape uncaught — an uncaught exception here would
                    // otherwise propagate up through this coroutine.
                    android.util.Log.e("ScreenshotWatcher", "Failed to process $uri", e)
                    NotificationHelper.showManualTagNotification(applicationContext, uri, takenAtMs)
                }
            }
        }
    }

    private fun looksLikeScreenshot(displayName: String, relativePath: String): Boolean {
        val lowerName = displayName.lowercase()
        val lowerPath = relativePath.lowercase()
        return lowerName.contains("screenshot") || lowerPath.contains("screenshot")
    }

    private suspend fun processScreenshot(repository: ScreenshotRepository, uri: Uri, takenAtMs: Long) {
        // Belt-and-braces dedupe: never file the same source screenshot twice,
        // regardless of how this function ended up being called for it again.
        if (repository.findByOriginalUri(uri.toString()) != null) return

        var result = detector.detect(takenAtMs)
        if (result is DetectionResult.Unknown) {
            // UsageStatsManager events can lag a second or two behind the actual
            // app switch. Give detection one more chance before giving up and
            // asking the user — this is what previously caused screenshots to
            // fall back to "couldn't detect" and then get auto-filed moments later.
            delay(RETRY_DELAY_MS)
            result = detector.detect(takenAtMs)
        }

        when (result) {
            is DetectionResult.Detected -> {
                // Copy the file FIRST. Only write App/Version rows once we know the
                // copy actually succeeded — otherwise a failed copy leaves behind an
                // empty "ghost" version with 0 screenshots, which is confusing and
                // was happening before this fix.
                val organizedUri = organizer.organize(uri, result.appLabel, result.versionLabel, takenAtMs)
                if (organizedUri != null) {
                    val appEntity = repository.getOrCreateApp(result.appLabel, result.packageName)
                    val versionEntity = repository.getOrCreateVersion(
                        appEntity.id,
                        result.versionLabel,
                        result.versionCode
                    )
                    repository.recordScreenshot(
                        versionId = versionEntity.id,
                        mediaUri = organizedUri.toString(),
                        originalUri = uri.toString(),
                        takenAt = takenAtMs,
                        wasAutoDetected = true
                    )
                } else {
                    android.util.Log.e("ScreenshotWatcher", "organize() failed for $uri")
                    NotificationHelper.showManualTagNotification(applicationContext, uri, takenAtMs)
                }
            }

            is DetectionResult.DetectedAppOnly -> {
                // We know the app but not a version — still worth a manual nudge so the
                // user can pick/confirm a version label rather than guessing "unversioned".
                NotificationHelper.showManualTagNotification(applicationContext, uri, takenAtMs)
            }

            DetectionResult.Unknown -> {
                NotificationHelper.showManualTagNotification(applicationContext, uri, takenAtMs)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "gitscreen_watcher_prefs"
        private const val PREF_LAST_ROW_ID = "last_processed_row_id"
        private const val DEBOUNCE_MS = 600L
        private const val RETRY_DELAY_MS = 2500L
    }
}
