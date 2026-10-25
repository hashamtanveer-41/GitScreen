package com.zivxio.gitscreen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.zivxio.gitscreen.util.Permissions

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Permissions.hasAllCorePermissions(context)) return

        val serviceIntent = Intent(context, ScreenshotWatcherService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
