package com.grude.app.actions

import android.net.Uri
import com.grude.app.api.GitHubApiClient
import com.grude.app.git.LocalGitEngine
import com.grude.app.model.GitHubRepo
import com.grude.app.model.WorkflowArtifact
import com.grude.app.model.WorkflowRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class OneButtonPipelineState {
    data object Idle : OneButtonPipelineState()
    data class Running(val stage: String, val progress: Float? = null, val run: WorkflowRun? = null) : OneButtonPipelineState()
    data class ApkReady(val run: WorkflowRun, val artifact: WorkflowArtifact, val apkFile: File) : OneButtonPipelineState()
    data class Error(val message: String) : OneButtonPipelineState()
}

/**
 * Orchestrates the core Grude workflow:
 * Local phone folder -> Single-Button Push -> Auto-poll GitHub Actions run -> Instant APK download & extraction.
 */
class PushAndBuildOrchestrator(
    private val gitEngine: LocalGitEngine,
    private val apiClient: GitHubApiClient,
    private val apkManager: ApkArtifactManager
) {
    private val _state = MutableStateFlow<OneButtonPipelineState>(OneButtonPipelineState.Idle)
    val state: StateFlow<OneButtonPipelineState> = _state.asStateFlow()

    fun reset() {
        _state.value = OneButtonPipelineState.Idle
    }

    suspend fun executePushAndWatchApk(
        folderUri: Uri,
        repo: GitHubRepo,
        commitMessage: String,
        username: String,
        token: String,
        autoInjectApkWorkflow: Boolean
    ) {
        _state.value = OneButtonPipelineState.Running("Preparing local folder push...")

        val commitSha = gitEngine.pushLocalFolderToRepo(
            folderTreeUri = folderUri,
            remoteHttpsUrl = repo.cloneUrl,
            repoFullName = repo.fullName,
            branchName = repo.defaultBranch.ifBlank { "main" },
            commitMessage = commitMessage.ifBlank { "Push from Grude mobile" },
            username = username,
            token = token,
            autoInjectApkWorkflow = autoInjectApkWorkflow,
            onStepUpdate = { msg -> _state.value = OneButtonPipelineState.Running(msg) }
        ).getOrElse { err ->
            _state.value = OneButtonPipelineState.Error("Push failed: ${err.message}")
            return
        }

        _state.value = OneButtonPipelineState.Running("Code pushed (${commitSha.take(7)}). Waiting for GitHub Actions run...")

        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        var matchedRun: WorkflowRun? = null

        // Poll up to ~12 minutes (72 * 10s) for workflow discovery and completion
        repeat(72) { attempt ->
            delay(if (attempt == 0) 4000L else 10_000L)
            val runs = apiClient.listWorkflowRuns(owner, repo.name).getOrNull().orEmpty()
            val candidate = runs.firstOrNull { it.headSha.startsWith(commitSha.take(7)) } ?: runs.firstOrNull()
            if (candidate != null) {
                matchedRun = candidate
                when (candidate.status) {
                    "completed" -> {
                        if (candidate.conclusion == "success") {
                            fetchAndExtractApk(owner, repo.name, candidate)
                        } else {
                            _state.value = OneButtonPipelineState.Error(
                                "Workflow finished with conclusion: ${candidate.conclusion ?: "unknown"}. Open Actions tab to inspect job logs."
                            )
                        }
                        return
                    }
                    else -> {
                        _state.value = OneButtonPipelineState.Running(
                            stage = "Workflow '${candidate.name ?: "Build"}' is ${candidate.status}...",
                            run = candidate
                        )
                    }
                }
            }
        }

        val statusNote = matchedRun?.status ?: "not triggered"
        _state.value = OneButtonPipelineState.Error("Timed out waiting for workflow (last status: $statusNote).")
    }

    private suspend fun fetchAndExtractApk(owner: String, repoName: String, run: WorkflowRun) {
        _state.value = OneButtonPipelineState.Running("Build succeeded! Fetching APK artifact...", run = run)
        val artifacts = apiClient.listRunArtifacts(owner, repoName, run.id).getOrElse { err ->
            _state.value = OneButtonPipelineState.Error("Failed to list artifacts: ${err.message}")
            return
        }

        val apkArtifact = artifacts.firstOrNull { !it.expired && it.name.contains("apk", ignoreCase = true) }
            ?: artifacts.firstOrNull { !it.expired }

        if (apkArtifact == null) {
            _state.value = OneButtonPipelineState.Error("Build succeeded, but no downloadable artifacts were uploaded.")
            return
        }

        val extracted = apkManager.downloadAndExtractApk(
            owner = owner,
            repo = repoName,
            artifactId = apkArtifact.id,
            artifactName = apkArtifact.name,
            onProgress = { pct ->
                _state.value = OneButtonPipelineState.Running(
                    stage = "Downloading ${apkArtifact.name} (${(pct * 100).toInt()}%)...",
                    progress = pct,
                    run = run
                )
            }
        ).getOrElse { err ->
            _state.value = OneButtonPipelineState.Error("APK download failed: ${err.message}")
            return
        }

        _state.value = OneButtonPipelineState.ApkReady(
            run = run,
            artifact = apkArtifact,
            apkFile = extracted.first()
        )
    }
}
