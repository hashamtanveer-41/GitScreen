package com.zivxio.gitscreen.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.zivxio.gitscreen.data.repository.DetectionResult

/**
 * Figures out which app was on screen right before a screenshot was taken.
 *
 * Screenshots don't carry any metadata about the app being captured, so the
 * best available signal is "which app was last brought to the foreground"
 * according to [UsageStatsManager]. This requires the user to grant the
 * special "Usage Access" permission (Settings.ACTION_USAGE_ACCESS_SETTINGS).
 *
 * This is a heuristic, not a guarantee — it will occasionally get it wrong
 * (e.g. quick app switches, split-screen). When it can't find a confident
 * answer it returns [DetectionResult.Unknown] so the caller can fall back to
 * a manual prompt.
 */
class ForegroundAppDetector(private val context: Context) {

    /** Packages that are never the "app being screenshotted" (launchers, system UI, us, screenshot overlays). */
    private val ignoredPackagePrefixes = listOf(
        "com.android.systemui",
        "com.zivxio.gitscreen",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.oneplus.launcher",
        "com.huawei.android.launcher",
        "com.motorola.launcher3",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.capture"
    )

    private val dynamicIgnoredPackages = mutableSetOf<String>()

    private fun isIgnoredPackage(pkg: String): Boolean {
        if (ignoredPackagePrefixes.any { pkg.startsWith(it) }) return true
        if (dynamicIgnoredPackages.contains(pkg)) return true

        return try {
            val pm = context.packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homeActivities = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in homeActivities) {
                val homePkg = resolveInfo.activityInfo?.packageName
                if (homePkg != null) {
                    dynamicIgnoredPackages.add(homePkg)
                    if (pkg == homePkg) return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Looks at usage events and stats to determine which app was on screen around [screenshotTimeMs].
     * Uses a multi-tiered strategy:
     * 1. UsageEvents query in an expanded window for real-time foreground transitions.
     * 2. UsageStats fallback inspection for ongoing/prolonged foreground sessions.
     */
    fun detectForegroundPackage(screenshotTimeMs: Long, windowMs: Long = 60_000L): String? {
        if (!hasUsageAccessPermission()) return null

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val effectiveTime = if (screenshotTimeMs > 0L && screenshotTimeMs <= now + 5_000L) screenshotTimeMs else now

        // Tier 1: Query usage events
        try {
            val events = usm.queryEvents(effectiveTime - windowMs, effectiveTime + 2_000L)
            var lastForegroundPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)

                if (isForegroundEvent && event.timeStamp <= effectiveTime + 1_500L) {
                    val pkg = event.packageName
                    if (pkg != null && !isIgnoredPackage(pkg)) {
                        lastForegroundPackage = pkg
                    }
                }
            }
            if (lastForegroundPackage != null) {
                return lastForegroundPackage
            }
        } catch (e: Exception) {
            android.util.Log.w("ForegroundAppDetector", "Failed to query usage events", e)
        }

        // Tier 2: Fallback to queryUsageStats (handles when user has been using the same app without recent transition)
        try {
            val statsList = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                effectiveTime - (1000L * 60 * 60), // Look back 1 hour
                effectiveTime + 2_000L
            )
            if (!statsList.isNullOrEmpty()) {
                val candidate = statsList
                    .filter { it.packageName != null && !isIgnoredPackage(it.packageName) }
                    .filter { it.lastTimeUsed in 1..(effectiveTime + 2_000L) }
                    .maxByOrNull { it.lastTimeUsed }

                if (candidate != null) {
                    return candidate.packageName
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ForegroundAppDetector", "Failed to query usage stats fallback", e)
        }

        return null
    }

    /** Resolves a package name into a friendly label + versionName/versionCode, if installed. */
    fun resolveAppDetails(packageName: String): DetectionResult {
        val pm = context.packageManager
        return try {
            val info = pm.getPackageInfo(packageName, 0)
            // Note: PackageInfo.applicationInfo is nullable in newer SDK stubs (compileSdk 36+),
            // so fetch it directly instead of relying on that field being non-null.
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val versionName = info.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }

            if (versionName.isNullOrBlank()) {
                DetectionResult.DetectedAppOnly(packageName = packageName, appLabel = label)
            } else {
                DetectionResult.Detected(
                    packageName = packageName,
                    appLabel = label,
                    versionLabel = versionName,
                    versionCode = versionCode
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            DetectionResult.Unknown
        }
    }

    /** Convenience: end-to-end detection for a screenshot timestamp. */
    fun detect(screenshotTimeMs: Long): DetectionResult {
        val pkg = detectForegroundPackage(screenshotTimeMs) ?: return DetectionResult.Unknown
        return resolveAppDetails(pkg)
    }
}
