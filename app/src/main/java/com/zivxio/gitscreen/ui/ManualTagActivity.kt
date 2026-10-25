package com.zivxio.gitscreen.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zivxio.gitscreen.GitScreenApp
import com.zivxio.gitscreen.detection.ScreenshotOrganizer
import com.zivxio.gitscreen.ui.screens.ManualTagScreen
import com.zivxio.gitscreen.ui.theme.GitScreenTheme
import com.zivxio.gitscreen.ui.viewmodel.ManualTagViewModel

/**
 * Small standalone activity opened from the "couldn't detect app" notification.
 * Deliberately lightweight — one screen, one job: let the user tag a single
 * screenshot and get out.
 */
class ManualTagActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAKEN_AT = "extra_taken_at"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screenshotUri: Uri = intent?.data ?: run { finish(); return }
        val takenAt = intent?.getLongExtra(EXTRA_TAKEN_AT, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        val app = application as GitScreenApp
        val organizer = ScreenshotOrganizer(applicationContext)

        setContent {
            GitScreenTheme {
                val viewModel: ManualTagViewModel = viewModel(
                    factory = ManualTagViewModel.Factory(app.repository, organizer, screenshotUri, takenAt)
                )
                ManualTagScreen(viewModel = viewModel, onSaved = { finish() })
            }
        }
    }
}
