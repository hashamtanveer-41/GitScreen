package com.zivxio.gitscreen.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zivxio.gitscreen.data.repository.AppSummary
import com.zivxio.gitscreen.ui.viewmodel.HomeViewModel
import com.zivxio.gitscreen.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAppClick: (appId: Long, appName: String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionsOk by remember { mutableStateOf(Permissions.hasAllCorePermissions(context)) }
    var appToDelete by remember { mutableStateOf<AppSummary?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsOk = Permissions.hasAllCorePermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (appToDelete != null) {
        val app = appToDelete!!
        AlertDialog(
            onDismissRequest = { appToDelete = null },
            title = { Text("Delete App") },
            text = {
                Text(
                    "Are you sure you want to delete \"${app.displayName}\"? " +
                        "This will permanently delete all ${app.versionCount} version(s) and ${app.screenshotCount} screenshot(s)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteApp(app.appId)
                        appToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { appToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitScreen") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!permissionsOk) {
                PermissionsBanner(onRecheck = { permissionsOk = Permissions.hasAllCorePermissions(context) })
            }

            if (apps.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(apps, key = { it.appId }) { app ->
                        AppRow(
                            app = app,
                            onClick = { onAppClick(app.appId, app.displayName) },
                            onLongClick = { appToDelete = app }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (app.latestThumbnailUri != null) {
                    AsyncImage(
                        model = app.latestThumbnailUri,
                        contentDescription = app.displayName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                }
            }
            Spacer(modifier = Modifier.padding(start = 12.dp))
            Column {
                Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${app.versionCount} version${if (app.versionCount == 1) "" else "s"} · " +
                        "${app.screenshotCount} screenshot${if (app.screenshotCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("No screenshots organized yet.", color = Color.Gray)
            Text("Take a screenshot of the app you're building to get started.", color = Color.Gray)
        }
    }
}

@Composable
private fun PermissionsBanner(onRecheck: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "GitScreen needs the Usage Access permission to detect which app a " +
                    "screenshot belongs to, plus gallery and notification access.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = {
                    context.startActivity(Permissions.usageAccessSettingsIntent(context))
                }) {
                    Text("Grant Usage Access")
                }
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Button(onClick = onRecheck) {
                    Text("I've granted it")
                }
            }
        }
    }
}
