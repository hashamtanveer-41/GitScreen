package com.zivxio.gitscreen

import android.app.Application
import com.zivxio.gitscreen.data.db.AppDatabase
import com.zivxio.gitscreen.data.repository.ScreenshotRepository
import com.zivxio.gitscreen.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * MVP note: dependencies are wired manually here (no Hilt/Koin yet) to keep the
 * scaffold simple. If the app grows, swap this for a proper DI graph.
 */
class GitScreenApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: ScreenshotRepository by lazy {
        ScreenshotRepository(
            appDao = database.appDao(),
            versionDao = database.versionDao(),
            screenshotDao = database.screenshotDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        // One-time tidy-up: an earlier bug could create an app/version row before
        // confirming its screenshot copy succeeded, leaving empty "ghost" entries
        // (0 screenshots) behind. This is safe to run on every launch — it only
        // ever removes entries that genuinely have no screenshots.
        appScope.launch { repository.cleanupEmptyEntries() }
    }
}
