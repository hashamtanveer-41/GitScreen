package com.zivxio.gitscreen.data.repository

import com.zivxio.gitscreen.data.db.AppDao
import com.zivxio.gitscreen.data.db.AppEntity
import com.zivxio.gitscreen.data.db.ScreenshotDao
import com.zivxio.gitscreen.data.db.ScreenshotEntity
import com.zivxio.gitscreen.data.db.VersionDao
import com.zivxio.gitscreen.data.db.VersionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for organized screenshot data. The service and the UI
 * both go through this class so file-organizing logic lives in one place.
 */
class ScreenshotRepository(
    private val appDao: AppDao,
    private val versionDao: VersionDao,
    private val screenshotDao: ScreenshotDao
) {

    fun observeApps(): Flow<List<AppEntity>> = appDao.observeAll()

    fun observeVersions(appId: Long): Flow<List<VersionEntity>> = versionDao.observeForApp(appId)

    fun observeScreenshots(versionId: Long): Flow<List<ScreenshotEntity>> =
        screenshotDao.observeForVersion(versionId)

    /** Home screen list: one summary row per app, most recently active apps included. */
    fun observeAppSummaries(): Flow<List<AppSummary>> =
        appDao.observeAll().map { apps ->
            apps.map { app ->
                AppSummary(
                    appId = app.id,
                    displayName = app.displayName,
                    packageName = app.packageName,
                    versionCount = versionDao.observeForApp(app.id).first().size,
                    screenshotCount = screenshotDao.countForApp(app.id),
                    latestThumbnailUri = screenshotDao.latestThumbnailForApp(app.id)
                )
            }
        }

    suspend fun getVersionSummaries(appId: Long): List<VersionSummary> =
        versionDao.observeForApp(appId).first().map { v ->
            VersionSummary(
                versionId = v.id,
                versionLabel = v.versionLabel,
                screenshotCount = screenshotDao.countForVersion(v.id)
            )
        }

    /**
     * True if this exact source screenshot has already been organized.
     * Callers should check this before doing any file copying/detection work so
     * a screenshot never gets filed twice, whether that's because the watcher
     * service saw the same MediaStore row more than once, or because the user
     * manually tagged something auto-detection had already picked up.
     */
    suspend fun findByOriginalUri(originalUri: String) = screenshotDao.getByOriginalUri(originalUri)

    /**
     * Removes any version with zero screenshots and any app left with zero
     * versions afterwards. Run once at app startup to clean up "ghost" entries
     * an earlier bug could have created (see ScreenshotWatcherService history).
     * Never touches a version/app that actually has screenshots recorded.
     */
    suspend fun cleanupEmptyEntries() {
        versionDao.deleteVersionsWithNoScreenshots()
        appDao.deleteAppsWithNoVersions()
    }

    /**
     * Finds or creates the App + Version rows for the given identifiers, then
     * records a screenshot under that version. Returns the new screenshot id.
     */
    suspend fun getOrCreateApp(displayName: String, packageName: String?): AppEntity {
        packageName?.let { pkg ->
            appDao.getByPackageName(pkg)?.let { return it }
        }
        // Build variants (e.g. com.example.app vs com.example.app.dev) usually
        // share the same <application android:label>, so two distinct apps can
        // otherwise land under identical-looking Home screen entries. Disambiguate
        // with the package name the moment a collision shows up.
        val finalDisplayName = if (packageName != null) {
            val collides = appDao.getAllOnce().any {
                it.displayName.equals(displayName, ignoreCase = true) && it.packageName != null && it.packageName != packageName
            }
            if (collides) "$displayName ($packageName)" else displayName
        } else {
            displayName
        }
        val id = appDao.insert(AppEntity(displayName = finalDisplayName, packageName = packageName))
        return AppEntity(id = id, displayName = finalDisplayName, packageName = packageName)
    }

    suspend fun getOrCreateVersion(appId: Long, versionLabel: String, versionCode: Long?): VersionEntity {
        versionDao.getByLabel(appId, versionLabel)?.let { return it }
        val id = versionDao.insert(
            VersionEntity(appId = appId, versionLabel = versionLabel, versionCode = versionCode)
        )
        return VersionEntity(id = id, appId = appId, versionLabel = versionLabel, versionCode = versionCode)
    }

    suspend fun recordScreenshot(
        versionId: Long,
        mediaUri: String,
        originalUri: String,
        takenAt: Long,
        wasAutoDetected: Boolean
    ): Long = screenshotDao.insert(
        ScreenshotEntity(
            versionId = versionId,
            mediaUri = mediaUri,
            originalUri = originalUri,
            takenAt = takenAt,
            wasAutoDetected = wasAutoDetected
        )
    )
}
