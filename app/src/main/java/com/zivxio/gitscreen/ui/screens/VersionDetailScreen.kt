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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zivxio.gitscreen.data.db.ScreenshotEntity
import com.zivxio.gitscreen.ui.viewmodel.VersionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionDetailScreen(
    viewModel: VersionDetailViewModel,
    onBack: () -> Unit
) {
    val screenshots by viewModel.screenshots.collectAsState()

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
                    ScreenshotThumb(shot)
                }
            }
        }
    }
}

@Composable
private fun ScreenshotThumb(shot: ScreenshotEntity) {
    AsyncImage(
        model = shot.mediaUri,
        contentDescription = "Screenshot taken ${shot.takenAt}",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
    )
}
