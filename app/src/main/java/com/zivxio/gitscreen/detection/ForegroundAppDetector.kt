package com.zivxio.gitscreen.detection

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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

    /** Packages that are never the "app being screenshotted" (launchers, system UI, us). */
    private val ignoredPackagePrefixes = listOf(
        "com.android.systemui",
        "com.zivxio.gitscreen",
        // The recents/overview UI ("Quickstep" on Pixel/AOSP gesture-nav devices) can
        // briefly register as foreground during the screenshot gesture itself, which
        // was causing real screenshots to get misfiled under "Quickstep" instead of
        // the app actually on screen.
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher"
    )

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
     * Looks at usage events in a small window before [screenshotTimeMs] and returns
     * the last package that moved to the foreground, if any.
     */
    fun detectForegroundPackage(screenshotTimeMs: Long, windowMs: Long = 15_000L): String? {
        if (!hasUsageAccessPermission()) return null

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(screenshotTimeMs - windowMs, screenshotTimeMs + 1_000L)

            var lastForegroundPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)

                if (isForegroundEvent && event.timeStamp <= screenshotTimeMs) {
                    val pkg = event.packageName
                    if (ignoredPackagePrefixes.none { pkg.startsWith(it) }) {
                        lastForegroundPackage = pkg
                    }
                }
            }
            lastForegroundPackage
        } catch (e: Exception) {
            // Usage access can be revoked mid-session by the user or the OS; never let
            // this take down the caller's coroutine scope over a detection hiccup.
            android.util.Log.w("ForegroundAppDetector", "Failed to query usage events", e)
            null
        }
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
