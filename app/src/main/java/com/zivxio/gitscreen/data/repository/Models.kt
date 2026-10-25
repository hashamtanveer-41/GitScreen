package com.zivxio.gitscreen.data.repository

/** Lightweight UI-facing model: an app plus a quick summary for the home list. */
data class AppSummary(
    val appId: Long,
    val displayName: String,
    val packageName: String?,
    val versionCount: Int,
    val screenshotCount: Int,
    val latestThumbnailUri: String?
)

/** UI-facing model: a version bucket plus how many screenshots it holds. */
data class VersionSummary(
    val versionId: Long,
    val versionLabel: String,
    val screenshotCount: Int
)

/** Result of trying to auto-detect the app + version for a freshly taken screenshot. */
sealed class DetectionResult {
    data class Detected(
        val packageName: String,
        val appLabel: String,
        val versionLabel: String,
        val versionCode: Long?
    ) : DetectionResult()

    /** Foreground app detection worked but the app has no readable version info. */
    data class DetectedAppOnly(
        val packageName: String,
        val appLabel: String
    ) : DetectionResult()

    object Unknown : DetectionResult()
}
