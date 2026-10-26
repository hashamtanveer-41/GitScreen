package com.zivxio.gitscreen.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Long-running foreground service that watches the MediaStore images collection
 * for new screenshots and organizes them automatically.
 */
class ScreenshotWatcherService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val scanMutex = Mutex()

    private lateinit var handlerThread: HandlerThread
    private lateinit var observer: ScreenshotContentObserver

    private lateinit var detector: ForegroundAppDetector
    private lateinit var organizer: ScreenshotOrganizer
    private lateinit var prefs: android.content.SharedPreferences

    @Volatile
    private var lastProcessedRowId: Long = 0L

    // Tracks URIs that have already been notified or processed to guarantee no loops
    private val notifiedUris = Collections.synchronizedSet(mutableSetOf<String>())

    private val scanScheduled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val retriggerRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(applicationContext)
        organizer = ScreenshotOrganizer(applicationContext)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize lastProcessedRowId synchronously before registering the observer.
        // Fall back to the latest row id currently in MediaStore so we NEVER scan past
        // gallery history or spam notifications for old photos.
        val savedId = prefs.getLong(PREF_LAST_ROW_ID, -1L)
        if (savedId > 0L) {
            lastProcessedRowId = savedId
        } else {
            val latest = latestRowId() ?: 0L
            lastProcessedRowId = latest
            prefs.edit().putLong(PREF_LAST_ROW_ID, latest).commit()
        }

        handlerThread = HandlerThread("gitscreen-observer").apply { start() }
        observer = ScreenshotContentObserver(Handler(handlerThread.looper)) {
            scheduleDebouncedScan()
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
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

    private fun scheduleDebouncedScan() {
        if (!scanScheduled.compareAndSet(false, true)) {
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
        return try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media._ID} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.w("ScreenshotWatcher", "Failed to query latest row ID", e)
            null
        }
    }

    private fun persistLastProcessedRowId() {
        prefs.edit().putLong(PREF_LAST_ROW_ID, lastProcessedRowId).commit()
    }

    private suspend fun checkForNewScreenshots() {
        scanMutex.withLock {
            if (lastProcessedRowId <= 0L) {
                val latest = latestRowId() ?: 0L
                lastProcessedRowId = latest
                persistLastProcessedRowId()
            }

            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.IS_PENDING
                )
            } else {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_TAKEN
                )
            }

            // Exclude pending images on Android Q+ so we don't attempt to process while writing
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media._ID} > ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
            } else {
                "${MediaStore.Images.Media._ID} > ?"
            }
            val selectionArgs = arrayOf(lastProcessedRowId.toString())

            val newRows = mutableListOf<Triple<Long, Uri, Long>>()
            var highestSeenId = lastProcessedRowId

            try {
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media._ID} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        @Suppress("DEPRECATION")
                        cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    }
                    val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: ""
                        val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                        highestSeenId = maxOf(highestSeenId, id)

                        if (path.contains("GitScreen") || name.startsWith("gitscreen_")) {
                            // Our own organized copy — ignore, avoid infinite loop.
                            continue
                        }
                        if (!looksLikeScreenshot(name, path)) {
                            continue
                        }

                        val dateAddedSec = cursor.getLong(dateAddedCol)
                        val dateTakenMs = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                        val takenAtMs = if (dateTakenMs > 0L) dateTakenMs else (dateAddedSec * 1000L)

                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        newRows += Triple(id, uri, takenAtMs)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenshotWatcher", "Failed to query MediaStore", e)
            }

            lastProcessedRowId = highestSeenId
            persistLastProcessedRowId()

            val app = applicationContext as GitScreenApp
            val now = System.currentTimeMillis()

            for ((_, uri, takenAtMs) in newRows) {
                // Safeguard: only process screenshots taken in the last 10 minutes to prevent loops/spam from historical photos
                if (now - takenAtMs > 10 * 60 * 1000L) {
                    continue
                }
                // Never re-process a URI that has already been notified or organized
                if (notifiedUris.contains(uri.toString()) || app.repository.findByOriginalUri(uri.toString()) != null) {
                    continue
                }

                try {
                    processScreenshot(app.repository, uri, takenAtMs)
                } catch (e: Exception) {
                    android.util.Log.e("ScreenshotWatcher", "Failed to process $uri", e)
                    notifiedUris.add(uri.toString())
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
        if (repository.findByOriginalUri(uri.toString()) != null) return
        if (notifiedUris.contains(uri.toString())) return

        var result = detector.detect(takenAtMs)
        if (result is DetectionResult.Unknown) {
            delay(RETRY_DELAY_MS)
            result = detector.detect(takenAtMs)
        }

        when (result) {
            is DetectionResult.Detected -> {
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
                    notifiedUris.add(uri.toString())
                    NotificationHelper.showManualTagNotification(
                        applicationContext,
                        uri,
                        takenAtMs,
                        suggestedAppName = result.appLabel
                    )
                }
            }

            is DetectionResult.DetectedAppOnly -> {
                notifiedUris.add(uri.toString())
                NotificationHelper.showManualTagNotification(
                    applicationContext,
                    uri,
                    takenAtMs,
                    suggestedAppName = result.appLabel
                )
            }

            DetectionResult.Unknown -> {
                notifiedUris.add(uri.toString())
                NotificationHelper.showManualTagNotification(applicationContext, uri, takenAtMs)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "gitscreen_watcher_prefs"
        private const val PREF_LAST_ROW_ID = "last_processed_row_id"
        private const val DEBOUNCE_MS = 600L
        private const val RETRY_DELAY_MS = 1500L
    }
}
