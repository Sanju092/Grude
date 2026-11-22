package com.grude.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grude.app.actions.OneButtonPipelineState
import com.grude.app.ui.GrudeViewModel

/**
 * Hero screen for Grude's core end-to-end flow:
 * 1. Create or pick a GitHub repository
 * 2. Select a local mobile project folder (SAF)
 * 3. Toggle auto-injection of .github/workflows/build-apk.yml
 * 4. Tap Single-Button Push -> Watch Workflow -> Install APK instantly
 */
@Composable
fun PushAndBuildScreen(
    viewModel: GrudeViewModel,
    onPickLocalFolder: () -> Unit
) {
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val selectedFolder by viewModel.selectedFolderUri.collectAsState()
    val pipelineState by viewModel.pipelineState.collectAsState()

    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var repoDropdownExpanded by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("Push from Grude mobile") }
    var autoInjectApkWorkflow by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("One-Button Push & APK Build", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Create a repository, pick your local project folder, push with one tap, and install the compiled Android APK as soon as GitHub Actions finishes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Step 1: Target Repository
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Target GitHub Repository", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { repoDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedRepo?.fullName ?: "Select Repository")
                        }
                        DropdownMenu(
                            expanded = repoDropdownExpanded,
                            onDismissRequest = { repoDropdownExpanded = false }
                        ) {
                            repos.forEach { repo ->
                                DropdownMenuItem(
                                    text = { Text(repo.fullName) },
                                    onClick = {
                                        viewModel.selectRepo(repo)
                                        repoDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    FilledTonalButton(onClick = { showCreateRepoDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }
        }

        // Step 2: Mobile Project Folder + Workflow Toggle
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("2. Local Mobile Folder", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onPickLocalFolder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedFolder != null) "Change Selected Folder" else "Pick Folder on Device")
                }
                if (selectedFolder != null) {
                    Text(
                        "Selected: ${selectedFolder?.lastPathSegment ?: selectedFolder.toString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit Message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Auto-inject build-apk.yml", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Adds .github/workflows/build-apk.yml automatically if your folder has no workflow file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoInjectApkWorkflow,
                        onCheckedChange = { autoInjectApkWorkflow = it }
                    )
                }
            }
        }

        // Step 3: Single-Button Push & Live APK Pipeline
        val isRunning = pipelineState is OneButtonPipelineState.Running
        Button(
            onClick = { viewModel.runOneButtonFolderPush(commitMessage, autoInjectApkWorkflow) },
            enabled = !isRunning && selectedFolder != null && selectedRepo != null,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Pushing & Building APK..." else "Push Folder & Build APK")
        }

        when (val state = pipelineState) {
            is OneButtonPipelineState.Idle -> Unit
            is OneButtonPipelineState.Running -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pipeline Active", style = MaterialTheme.typography.titleSmall)
                        Text(state.stage, style = MaterialTheme.typography.bodyMedium)
                        if (state.progress != null) {
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            is OneButtonPipelineState.ApkReady -> {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Android APK Ready!", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Artifact '${state.artifact.name}' extracted (${state.apkFile.name}).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.installApk(state.apkFile) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Android, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Install APK Now")
                            }
                            OutlinedButton(onClick = { viewModel.resetPipeline() }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
            is OneButtonPipelineState.Error -> {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pipeline Stopped", style = MaterialTheme.typography.titleSmall)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.resetPipeline() }) { Text("Dismiss") }
                    }
                }
            }
        }
    }

    if (showCreateRepoDialog) {
        CreateRepoDialog(
            onDismiss = { showCreateRepoDialog = false },
            onCreate = { name, desc, isPrivate ->
                viewModel.createRepo(name, desc, isPrivate)
                showCreateRepoDialog = false
            }
        )
    }
}

@Composable
private fun CreateRepoDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create GitHub Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text("Private repository")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, desc, isPrivate) }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
