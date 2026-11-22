package com.grude.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.grude.app.ui.GrudeViewModel

@Composable
fun ActionsScreen(viewModel: GrudeViewModel) {
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val runs by viewModel.workflowRuns.collectAsState()
    val jobs by viewModel.runJobs.collectAsState()
    val artifacts by viewModel.runArtifacts.collectAsState()
    val logText by viewModel.jobLogText.collectAsState()
    val apkProgress by viewModel.apkDownloadProgress.collectAsState()

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
                    Text("GitHub Actions & APKs", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        selectedRepo?.fullName ?: "Select a repository first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.refreshWorkflowRuns() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh runs")
                }
            }
        }

        if (apkProgress != null) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Downloading APK Artifact (${((apkProgress ?: 0f) * 100).toInt()}%)")
                        LinearProgressIndicator(progress = { apkProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (artifacts.isNotEmpty()) {
            item { Text("Downloadable Build Artifacts", style = MaterialTheme.typography.titleMedium) }
            items(artifacts, key = { it.id }) { artifact ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artifact.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${artifact.sizeInBytes / 1024} KB · ${if (artifact.expired) "Expired" else "Ready"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { viewModel.downloadAndInstallArtifact(artifact) },
                            enabled = !artifact.expired
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("APK")
                        }
                    }
                }
            }
        }

        if (jobs.isNotEmpty()) {
            item { Text("Selected Run Jobs", style = MaterialTheme.typography.titleMedium) }
            items(jobs, key = { it.id }) { job ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${job.name} (${job.conclusion ?: job.status})", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { viewModel.fetchJobLogs(job.id) }) { Text("View Logs") }
                        }
                        job.steps.forEach { step ->
                            Text(
                                "${step.number}. ${step.name} — ${step.conclusion ?: step.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (logText.isNotBlank()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Job Log Output (tail)", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(logText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { Text("Recent Workflow Runs", style = MaterialTheme.typography.titleMedium) }

        items(runs, key = { it.id }) { run ->
            ElevatedCard(
                onClick = { viewModel.inspectWorkflowRun(run) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(run.displayTitle.ifBlank { run.name ?: "Workflow #${run.id}" }, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${run.headBranch} · ${run.status} · ${run.conclusion ?: "running"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { viewModel.rerunWorkflow(run.id) }) {
                        Text("Re-run")
                    }
                }
            }
        }
    }
}

@Composable
fun ReposAndGitScreen(viewModel: GrudeViewModel) {
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val contents by viewModel.repoContents.collectAsState()
    val editingFile by viewModel.editingFile.collectAsState()
    val localStatus by viewModel.localGitStatus.collectAsState()
    val prs by viewModel.pullRequests.collectAsState()
    val issues by viewModel.issues.collectAsState()

    var currentPath by remember { mutableStateOf("") }
    var localCommitMsg by remember { mutableStateOf("Update from Grude") }

    editingFile?.let { (fileItem, initialContent) ->
        var text by remember(fileItem.sha) { mutableStateOf(initialContent) }
        var commitMsg by remember(fileItem.sha) { mutableStateOf("Edit ${fileItem.name} via Grude") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Editing ${fileItem.path}", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            OutlinedTextField(
                value = commitMsg,
                onValueChange = { commitMsg = it },
                label = { Text("Commit Message") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.closeFileEditor() }) { Text("Cancel") }
                Button(
                    onClick = { viewModel.saveEditedFile(text, commitMsg) },
                    modifier = Modifier.weight(1f)
                ) { Text("Commit File to GitHub") }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(selectedRepo?.fullName ?: "Repos & Local Git", style = MaterialTheme.typography.headlineSmall)
        }

        // Local JGit controls
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local JGit Workspace", style = MaterialTheme.typography.titleSmall)
                    Text(
                        localStatus?.let {
                            "Branch: ${it.branch} · Modified: ${it.modified.size} · Untracked: ${it.untracked.size}"
                        } ?: "Not cloned locally yet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.cloneSelectedRepo() }) { Text("Clone") }
                        OutlinedButton(onClick = { viewModel.pullSelectedRepo() }) { Text("Pull") }
                        Button(onClick = { viewModel.commitAndPushLocal(localCommitMsg) }) { Text("Commit & Push") }
                    }
                }
            }
        }

        // Repo files & build files (.github/workflows, build.gradle.kts, etc.)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Files: /${currentPath}", style = MaterialTheme.typography.titleMedium)
                if (currentPath.isNotEmpty()) {
                    TextButton(onClick = {
                        currentPath = currentPath.substringBeforeLast("/", "")
                        selectedRepo?.let { viewModel.loadRepoContents(it, currentPath) }
                    }) { Text("Up") }
                }
            }
        }

        items(contents, key = { it.path }) { item ->
            ElevatedCard(
                onClick = {
                    if (item.type == "dir") {
                        currentPath = item.path
                        selectedRepo?.let { viewModel.loadRepoContents(it, currentPath) }
                    } else {
                        viewModel.openFileForEditing(item.path)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null
                    )
                    Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (item.type == "file") {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
        }

        if (prs.isNotEmpty()) {
            item { Text("Open Pull Requests (${prs.size})", style = MaterialTheme.typography.titleMedium) }
            items(prs, key = { it.number }) { pr ->
                Text("#${pr.number} ${pr.title} (@${pr.user.login})", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (issues.isNotEmpty()) {
            item { Text("Open Issues (${issues.size})", style = MaterialTheme.typography.titleMedium) }
            items(issues, key = { it.number }) { issue ->
                Text("#${issue.number} ${issue.title} (@${issue.user.login})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
