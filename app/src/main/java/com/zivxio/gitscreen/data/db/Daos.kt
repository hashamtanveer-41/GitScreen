package com.zivxio.gitscreen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert
    suspend fun insert(app: AppEntity): Long

    @Query("SELECT * FROM apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppEntity?

    @Query("SELECT * FROM apps WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AppEntity?

    @Query("SELECT * FROM apps ORDER BY displayName ASC")
    fun observeAll(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps ORDER BY displayName ASC")
    suspend fun getAllOnce(): List<AppEntity>

    // Companion cleanup to deleteVersionsWithNoScreenshots() — once orphaned
    // versions are gone, this removes any app left with no versions at all.
    @Query("DELETE FROM apps WHERE id NOT IN (SELECT DISTINCT appId FROM versions)")
    suspend fun deleteAppsWithNoVersions()

    @Query("DELETE FROM apps WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface VersionDao {
    @Insert
    suspend fun insert(version: VersionEntity): Long

    @Query("SELECT * FROM versions WHERE appId = :appId AND versionLabel = :label LIMIT 1")
    suspend fun getByLabel(appId: Long, label: String): VersionEntity?

    @Query("SELECT * FROM versions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VersionEntity?

    @Query("SELECT * FROM versions WHERE appId = :appId ORDER BY createdAt DESC")
    fun observeForApp(appId: Long): Flow<List<VersionEntity>>

    // Cleans up "ghost" versions left behind by an earlier bug where a version
    // row could get created before its screenshot file copy was confirmed to
    // have succeeded. Safe to run any time — it only ever removes versions that
    // have zero screenshots.
    @Query("DELETE FROM versions WHERE id NOT IN (SELECT DISTINCT versionId FROM screenshots)")
    suspend fun deleteVersionsWithNoScreenshots()

    @Query("DELETE FROM versions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScreenshotDao {
    // IGNORE (rather than the default ABORT) means a duplicate originalUri insert
    // is silently dropped instead of crashing — the app-level dedupe check in
    // ScreenshotRepository/ScreenshotWatcherService is the primary guard, this is
    // the last-resort safety net in case two inserts still race each other.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(screenshot: ScreenshotEntity): Long

    @Query("SELECT * FROM screenshots WHERE originalUri = :originalUri LIMIT 1")
    suspend fun getByOriginalUri(originalUri: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScreenshotEntity?

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT mediaUri FROM screenshots WHERE versionId = :versionId")
    suspend fun getMediaUrisForVersion(versionId: Long): List<String>

    @Query(
        """
        SELECT s.mediaUri FROM screenshots s
        INNER JOIN versions v ON s.versionId = v.id
        WHERE v.appId = :appId
        """
    )
    suspend fun getMediaUrisForApp(appId: Long): List<String>

    @Query("SELECT * FROM screenshots WHERE versionId = :versionId ORDER BY takenAt DESC")
    fun observeForVersion(versionId: Long): Flow<List<ScreenshotEntity>>

    @Query("SELECT COUNT(*) FROM screenshots WHERE versionId = :versionId")
    suspend fun countForVersion(versionId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM screenshots s
        INNER JOIN versions v ON s.versionId = v.id
        WHERE v.appId = :appId
        """
    )
    suspend fun countForApp(appId: Long): Int

    @Query(
        """
        SELECT s.mediaUri FROM screenshots s
        INNER JOIN versions v ON s.versionId = v.id
        WHERE v.appId = :appId
        ORDER BY s.takenAt DESC LIMIT 1
        """
    )
    suspend fun latestThumbnailForApp(appId: Long): String?
}
