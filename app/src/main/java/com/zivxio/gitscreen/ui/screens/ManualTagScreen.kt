package com.zivxio.gitscreen.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zivxio.gitscreen.ui.viewmodel.ManualTagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTagScreen(
    viewModel: ManualTagViewModel,
    onSaved: () -> Unit
) {
    val existingApps by viewModel.existingApps.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    var appName by remember { mutableStateOf(viewModel.initialAppName) }
    var versionLabel by remember { mutableStateOf("") }

    LaunchedEffect(saveState) {
        if (saveState is ManualTagViewModel.SaveState.Saved ||
            saveState is ManualTagViewModel.SaveState.AlreadySaved
        ) {
            onSaved()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Tag this screenshot") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                "GitScreen couldn't figure out which app this belongs to. " +
                    "Give it an app name and version and it'll remember next time.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            AsyncImage(
                model = viewModel.screenshotUri,
                contentDescription = "Screenshot preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = appName,
                onValueChange = { appName = it },
                label = { Text("App name") },
                modifier = Modifier.fillMaxWidth()
            )

            if (existingApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow {
                    items(existingApps) { app ->
                        AssistChip(
                            onClick = { appName = app.displayName },
                            label = { Text(app.displayName) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = versionLabel,
                onValueChange = { versionLabel = it },
                label = { Text("Version (e.g. 1.0.0)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = saveState) {
                is ManualTagViewModel.SaveState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is ManualTagViewModel.SaveState.Saving -> {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
                }
                else -> Unit
            }

            Button(
                onClick = { viewModel.save(appName, versionLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = saveState !is ManualTagViewModel.SaveState.Saving
            ) {
                Text("Save to GitScreen")
            }
        }
    }
}
