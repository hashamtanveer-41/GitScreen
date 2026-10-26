package com.zivxio.gitscreen.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zivxio.gitscreen.service.ScreenshotWatcherService
import com.zivxio.gitscreen.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsageAccess by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var hasNotifications by remember { mutableStateOf(Permissions.hasNotificationPermission(context)) }
    var hasMedia by remember { mutableStateOf(Permissions.hasMediaPermission(context)) }
    var isIgnoringBattery by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }

    fun refreshPermissions() {
        hasUsageAccess = Permissions.hasUsageAccess(context)
        hasNotifications = Permissions.hasNotificationPermission(context)
        hasMedia = Permissions.hasMediaPermission(context)
        isIgnoringBattery = Permissions.isIgnoringBatteryOptimizations(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    val requestNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Permissions & System Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val allCore = hasUsageAccess && hasNotifications && hasMedia
                        Text(
                            text = if (allCore) "All core permissions active" else "Action required for automatic capture",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allCore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = "REQUIRED PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Usage Access
            PermissionItem(
                title = "Usage Access",
                description = "Identifies foreground apps and versions in real-time when screenshots are taken.",
                isGranted = hasUsageAccess,
                icon = Icons.Default.Visibility,
                onToggle = {
                    context.startActivity(Permissions.usageAccessSettingsIntent(context))
                }
            )

            // Notification Permission
            PermissionItem(
                title = "Notifications",
                description = "Required to keep the background watcher running and send capture tags.",
                isGranted = hasNotifications,
                icon = Icons.Default.Notifications,
                onToggle = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
                        requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Permissions.notificationSettingsIntent(context))
                    }
                }
            )

            // Media & Storage
            PermissionItem(
                title = "Media & Storage Access",
                description = "Allows GitScreen to detect screenshots and save organized copies into your gallery.",
                isGranted = hasMedia,
                icon = Icons.Default.Folder,
                onToggle = {
                    if (!hasMedia) {
                        requestMediaLauncher.launch(Permissions.mediaPermission())
                    } else {
                        context.startActivity(Permissions.appDetailsSettingsIntent(context))
                    }
                }
            )

            Text(
                text = "BACKGROUND RELIABILITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Battery Optimization
            PermissionItem(
                title = "Ignore Battery Optimizations",
                description = "Recommended so Android doesn't kill the watcher service when your screen is locked or idle.",
                isGranted = isIgnoringBattery,
                icon = Icons.Default.BatteryChargingFull,
                onToggle = {
                    if (!isIgnoringBattery) {
                        try {
                            context.startActivity(Permissions.requestIgnoreBatteryOptimizationsIntent(context))
                        } catch (_: Exception) {
                            context.startActivity(Permissions.appDetailsSettingsIntent(context))
                        }
                    } else {
                        context.startActivity(Permissions.appDetailsSettingsIntent(context))
                    }
                }
            )

            // Service Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Watcher Service", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Detects screenshot gallery events automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasMedia) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (hasMedia) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasMedia) "Running" else "Stopped",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasMedia) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Permissions.hasMediaPermission(context)) {
                                val intent = Intent(context, ScreenshotWatcherService::class.java)
                                ContextCompat.startForegroundService(context, intent)
                                Toast.makeText(context, "Watcher service restarted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Grant Media permission first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart Watcher Service")
                    }
                }
            }

            // System App Info Link
            OutlinedButton(
                onClick = {
                    context.startActivity(Permissions.appDetailsSettingsIntent(context))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open App System Info")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "GitScreen v0.1.0 • Built for Android UI version control",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isGranted) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Not granted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isGranted,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
