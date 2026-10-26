package com.zivxio.gitscreen.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.zivxio.gitscreen.data.db.ScreenshotEntity
import com.zivxio.gitscreen.ui.viewmodel.VersionDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionDetailScreen(
    viewModel: VersionDetailViewModel,
    onBack: () -> Unit
) {
    val screenshots by viewModel.screenshots.collectAsState()
    var screenshotToDelete by remember { mutableStateOf<ScreenshotEntity?>(null) }
    var previewScreenshot by remember { mutableStateOf<ScreenshotEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

    if (screenshotToDelete != null) {
        val shot = screenshotToDelete!!
        AlertDialog(
            onDismissRequest = { screenshotToDelete = null },
            title = { Text("Delete Screenshot") },
            text = {
                Text("Are you sure you want to delete this screenshot taken on ${dateFormat.format(Date(shot.takenAt))}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScreenshot(shot.id)
                        screenshotToDelete = null
                        if (previewScreenshot?.id == shot.id) {
                            previewScreenshot = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { screenshotToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (previewScreenshot != null) {
        val shot = previewScreenshot!!
        Dialog(onDismissRequest = { previewScreenshot = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Screenshot Preview",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                dateFormat.format(Date(shot.takenAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { screenshotToDelete = shot }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = { previewScreenshot = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = shot.mediaUri,
                        contentDescription = "Full screenshot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("v${viewModel.versionLabel}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (screenshots.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No screenshots in this version yet.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(screenshots, key = { it.id }) { shot ->
                    ScreenshotThumb(
                        shot = shot,
                        onClick = { previewScreenshot = shot },
                        onLongClick = { screenshotToDelete = shot }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotThumb(
    shot: ScreenshotEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    AsyncImage(
        model = shot.mediaUri,
        contentDescription = "Screenshot taken ${shot.takenAt}",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}
