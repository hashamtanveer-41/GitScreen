package com.zivxio.gitscreen.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per app GitScreen has ever tagged a screenshot with.
 * [packageName] is null for apps that don't map to an installed package
 * (e.g. the user typed a free-text name like "Figma Prototype v2").
 */
@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val packageName: String?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A version "bucket" belonging to an app, e.g. "1.0.0" or "3.0.0".
 * This is what makes the app act like Git branches/tags for UI screenshots.
 */
@Entity(
    tableName = "versions",
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["id"],
            childColumns = ["appId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("appId")]
)
data class VersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appId: Long,
    val versionLabel: String,
    val versionCode: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A single organized screenshot.
 * [mediaUri] points at the copy GitScreen made inside its own organized folder
 * structure (Pictures/GitScreen/<App>/<Version>/...), not the original screenshot.
 */
@Entity(
    tableName = "screenshots",
    foreignKeys = [
        ForeignKey(
            entity = VersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["versionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("versionId"),
        // Guards against the same source screenshot ever being organized twice,
        // even if the watcher service races itself (multiple MediaStore change
        // events firing for one file write) or the user manually tags something
        // auto-detection already picked up in the background.
        Index(value = ["originalUri"], unique = true)
    ]
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val versionId: Long,
    val mediaUri: String,
    val originalUri: String,
    val takenAt: Long,
    val wasAutoDetected: Boolean
)
