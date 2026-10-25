package com.zivxio.gitscreen.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.zivxio.gitscreen.GitScreenApp
import com.zivxio.gitscreen.service.ScreenshotWatcherService
import com.zivxio.gitscreen.ui.navigation.GitScreenNavHost
import com.zivxio.gitscreen.ui.theme.GitScreenTheme
import com.zivxio.gitscreen.util.Permissions

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        maybeStartWatcherService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as GitScreenApp

        setContent {
            GitScreenTheme {
                GitScreenNavHost(repository = app.repository)
            }
        }

        requestRuntimePermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        maybeStartWatcherService()
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val toRequest = mutableListOf<String>()
        if (!Permissions.hasMediaPermission(this)) toRequest += Permissions.mediaPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Permissions.hasNotificationPermission(this)) {
            toRequest += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (toRequest.isNotEmpty()) {
            requestPermissions.launch(toRequest.toTypedArray())
        } else {
            maybeStartWatcherService()
        }
    }

    /**
     * The watcher service only needs media + notification permission to run; Usage
     * Access (for app detection) is optional — without it, every screenshot just
     * falls back to the manual tagging prompt, which still works fine.
     */
    private fun maybeStartWatcherService() {
        if (Permissions.hasMediaPermission(this)) {
            val intent = Intent(this, ScreenshotWatcherService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }
}
