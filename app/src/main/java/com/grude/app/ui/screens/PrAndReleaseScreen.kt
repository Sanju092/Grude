package com.grude.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grude.app.model.PullRequestItem
import com.grude.app.ui.GrudeViewModel
import com.grude.app.ui.components.SyntaxDiffViewer

@Composable
fun PrAndReleaseScreen(
    viewModel: GrudeViewModel,
    onPickReleaseApkFile: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedPr by viewModel.selectedPr.collectAsState()

    if (selectedPr != null) {
        PullRequestDiffView(pr = selectedPr!!, viewModel = viewModel)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Pull Requests") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Releases & APKs") })
        }

        when (selectedTab) {
            0 -> PullRequestsTab(viewModel = viewModel)
            1 -> ReleasesApkTab(viewModel = viewModel, onPickReleaseApkFile = onPickReleaseApkFile)
        }
    }
}

@Composable
private fun PullRequestsTab(viewModel: GrudeViewModel) {
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val prs by viewModel.pullRequests.collectAsState()
    val branches by viewModel.repoBranches.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pull Requests", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        selectedRepo?.fullName ?: "Select a repository first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = { viewModel.refreshPrsAndReleases() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        enabled = selectedRepo != null
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New PR")
                    }
                }
            }
        }

        if (prs.isEmpty()) {
            item {
                Text(
                    "No open pull requests in this repository. Tap New PR to open one between branches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(prs, key = { it.number }) { pr ->
            ElevatedCard(
                onClick = { viewModel.openPullRequestDiff(pr) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("#${pr.number} ${pr.title}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${pr.head.ref} -> ${pr.base.ref} · by @${pr.user.login}${if (pr.draft) " · Draft" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap to inspect syntax-highlighted diff & merge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePullRequestDialog(
            branches = branches.map { it.name },
            defaultBase = selectedRepo?.defaultBranch ?: "main",
            onDismiss = { showCreateDialog = false },
            onCreate = { title, head, base, body, draft ->
                viewModel.createPullRequest(title, head, base, body, draft)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PullRequestDiffView(pr: PullRequestItem, viewModel: GrudeViewModel) {
    val diffFiles by viewModel.prDiffFiles.collectAsState()
    var mergeMethod by remember { mutableStateOf("squash") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.closePullRequestDiff() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("PR #${pr.number}: ${pr.title}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pr.head.ref} -> ${pr.base.ref}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Merge controls
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("squash", "merge", "rebase").forEach { method ->
                            FilterChip(
                                selected = mergeMethod == method,
                                onClick = { mergeMethod = method },
                                label = { Text(method.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.mergePullRequest(pr.number, mergeMethod) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CallMerge, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Merge PR (${mergeMethod.uppercase()})")
                    }
                }
            }
        }

        item {
            Text("Changed Files (${diffFiles.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(diffFiles, key = { it.filename }) { file ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(file.filename, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("+${file.additions}", color = Color(0xFF059669), style = MaterialTheme.typography.labelMedium)
                            Text("-${file.deletions}", color = Color(0xFFDC2626), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    HorizontalDivider()
                    SyntaxDiffViewer(patch = file.patch)
                }
            }
        }
    }
}

@Composable
private fun ReleasesApkTab(
    viewModel: GrudeViewModel,
    onPickReleaseApkFile: () -> Unit
) {
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val releases by viewModel.releases.collectAsState()
    val selectedApkFile by viewModel.selectedReleaseApkFile.collectAsState()
    val isPublishing by viewModel.isPublishingRelease.collectAsState()

    var tagName by remember { mutableStateOf("v1.0.0") }
    var releaseTitle by remember { mutableStateOf("Release v1.0.0") }
    var releaseNotes by remember { mutableStateOf("Built and published directly from Grude Android.") }
    var isDraft by remember { mutableStateOf(false) }
    var isPrerelease by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Publish GitHub Release + APK", style = MaterialTheme.typography.headlineSmall)
            Text(
                selectedRepo?.fullName ?: "Select a repository first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tagName,
                        onValueChange = { tagName = it },
                        label = { Text("Tag Name (e.g. v1.0.0)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = releaseTitle,
                        onValueChange = { releaseTitle = it },
                        label = { Text("Release Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        label = { Text("Release Notes (Markdown)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isDraft, onCheckedChange = { isDraft = it })
                            Text("Draft")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isPrerelease, onCheckedChange = { isPrerelease = it })
                            Text("Pre-release")
                        }
                    }

                    OutlinedButton(
                        onClick = onPickReleaseApkFile,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedApkFile?.name ?: "Pick .APK File from Phone (or use latest build)")
                    }

                    Button(
                        onClick = {
                            viewModel.publishReleaseWithApk(
                                tagName = tagName,
                                title = releaseTitle,
                                notes = releaseNotes,
                                draft = isDraft,
                                prerelease = isPrerelease
                            )
                        },
                        enabled = !isPublishing && selectedRepo != null && tagName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.NewReleases, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isPublishing) "Publishing Release & Uploading APK..."
                            else if (selectedApkFile != null) "Publish Release & Upload APK"
                            else "Publish Release"
                        )
                    }
                }
            }
        }

        item {
            Text("Published Releases (${releases.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(releases, key = { it.id }) { release ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${release.tagName} · ${release.name ?: release.tagName}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!release.body.isNullOrBlank()) {
                        Text(release.body.take(180), style = MaterialTheme.typography.bodySmall)
                    }
                    release.assets.forEach { asset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "${asset.name} (${asset.size / 1024} KB · ${asset.downloadCount} downloads)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePullRequestDialog(
    branches: List<String>,
    defaultBase: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var head by remember { mutableStateOf(branches.firstOrNull { it != defaultBase } ?: "") }
    var base by remember { mutableStateOf(defaultBase) }
    var body by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Pull Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("PR Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = head,
                    onValueChange = { head = it },
                    label = { Text("Head Branch (source)") },
                    placeholder = { Text("feature/my-branch") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("Base Branch (target)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Description (optional)") },
                    minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft, onCheckedChange = { draft = it })
                    Text("Create as Draft PR")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && head.isNotBlank() && base.isNotBlank()) {
                        onCreate(title.trim(), head.trim(), base.trim(), body.trim(), draft)
                    }
                }
            ) { Text("Create PR") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
