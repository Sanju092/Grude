package com.grude.app.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grude.app.actions.ApkArtifactManager
import com.grude.app.actions.OneButtonPipelineState
import com.grude.app.actions.PushAndBuildOrchestrator
import com.grude.app.api.GitHubApiClient
import com.grude.app.auth.AuthVault
import com.grude.app.auth.DeviceFlowAuth
import com.grude.app.git.LocalGitEngine
import com.grude.app.git.LocalRepoStatus
import com.grude.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GrudeViewModel(application: Application) : AndroidViewModel(application) {

    val authVault = AuthVault(application)
    private val deviceFlowAuth = DeviceFlowAuth()

    val apiClient = GitHubApiClient(tokenProvider = {
        authVault.getActiveAccount()?.accessToken?.takeIf { it.isNotBlank() }
    })

    val gitEngine = LocalGitEngine(application)
    val apkManager = ApkArtifactManager(application, apiClient)
    private val orchestrator = PushAndBuildOrchestrator(gitEngine, apiClient, apkManager)

    val pipelineState: StateFlow<OneButtonPipelineState> = orchestrator.state

    private val _accounts = MutableStateFlow(authVault.listAccounts())
    val accounts: StateFlow<List<AccountProfile>> = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow(authVault.getActiveAccount())
    val activeAccount: StateFlow<AccountProfile?> = _activeAccount.asStateFlow()

    private val _repos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val repos: StateFlow<List<GitHubRepo>> = _repos.asStateFlow()

    private val _selectedRepo = MutableStateFlow<GitHubRepo?>(null)
    val selectedRepo: StateFlow<GitHubRepo?> = _selectedRepo.asStateFlow()

    private val _repoBranches = MutableStateFlow<List<BranchItem>>(emptyList())
    val repoBranches: StateFlow<List<BranchItem>> = _repoBranches.asStateFlow()

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _workflowRuns = MutableStateFlow<List<WorkflowRun>>(emptyList())
    val workflowRuns: StateFlow<List<WorkflowRun>> = _workflowRuns.asStateFlow()

    private val _runJobs = MutableStateFlow<List<WorkflowJob>>(emptyList())
    val runJobs: StateFlow<List<WorkflowJob>> = _runJobs.asStateFlow()

    private val _runArtifacts = MutableStateFlow<List<WorkflowArtifact>>(emptyList())
    val runArtifacts: StateFlow<List<WorkflowArtifact>> = _runArtifacts.asStateFlow()

    private val _jobLogText = MutableStateFlow("")
    val jobLogText: StateFlow<String> = _jobLogText.asStateFlow()

    private val _repoContents = MutableStateFlow<List<RepoContentItem>>(emptyList())
    val repoContents: StateFlow<List<RepoContentItem>> = _repoContents.asStateFlow()

    private val _editingFile = MutableStateFlow<Pair<RepoContentItem, String>?>(null)
    val editingFile: StateFlow<Pair<RepoContentItem, String>?> = _editingFile.asStateFlow()

    private val _pullRequests = MutableStateFlow<List<PullRequestItem>>(emptyList())
    val pullRequests: StateFlow<List<PullRequestItem>> = _pullRequests.asStateFlow()

    private val _selectedPr = MutableStateFlow<PullRequestItem?>(null)
    val selectedPr: StateFlow<PullRequestItem?> = _selectedPr.asStateFlow()

    private val _prDiffFiles = MutableStateFlow<List<PullRequestFile>>(emptyList())
    val prDiffFiles: StateFlow<List<PullRequestFile>> = _prDiffFiles.asStateFlow()

    private val _releases = MutableStateFlow<List<GitHubRelease>>(emptyList())
    val releases: StateFlow<List<GitHubRelease>> = _releases.asStateFlow()

    private val _selectedReleaseApkFile = MutableStateFlow<File?>(null)
    val selectedReleaseApkFile: StateFlow<File?> = _selectedReleaseApkFile.asStateFlow()

    private val _isPublishingRelease = MutableStateFlow(false)
    val isPublishingRelease: StateFlow<Boolean> = _isPublishingRelease.asStateFlow()

    private val _issues = MutableStateFlow<List<IssueItem>>(emptyList())
    val issues: StateFlow<List<IssueItem>> = _issues.asStateFlow()

    private val _localGitStatus = MutableStateFlow<LocalRepoStatus?>(null)
    val localGitStatus: StateFlow<LocalRepoStatus?> = _localGitStatus.asStateFlow()

    private val _deviceCode = MutableStateFlow<DeviceCodeResponse?>(null)
    val deviceCode: StateFlow<DeviceCodeResponse?> = _deviceCode.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _apkDownloadProgress = MutableStateFlow<Float?>(null)
    val apkDownloadProgress: StateFlow<Float?> = _apkDownloadProgress.asStateFlow()

    init {
        if (_activeAccount.value?.authMethod?.supportsApi == true) {
            refreshRepos()
        }
        viewModelScope.launch {
            pipelineState.collect { state ->
                if (state is OneButtonPipelineState.ApkReady) {
                    // Auto-populate the Releases tab with the freshly built APK for instant publishing
                    _selectedReleaseApkFile.value = state.apkFile
                }
            }
        }
    }

    fun clearBanner() { _statusBanner.value = null }

    fun setSelectedFolder(uri: Uri?) { _selectedFolderUri.value = uri }

    fun selectRepo(repo: GitHubRepo) {
        _selectedRepo.value = repo
        _selectedPr.value = null
        refreshWorkflowRuns(repo)
        loadRepoContents(repo, "")
        refreshPrsAndReleases(repo)
        refreshLocalStatus(repo)
    }

    // --- Authentication (All 5 Methods) ---

    fun signInWithPat(token: String) {
        viewModelScope.launch {
            val clean = token.trim()
            if (clean.isEmpty()) {
                _statusBanner.value = "Please enter a valid Personal Access Token."
                return@launch
            }
            val tempClient = GitHubApiClient { clean }
            tempClient.getAuthenticatedUser().fold(
                onSuccess = { user ->
                    val profile = AccountProfile(
                        id = UUID.randomUUID().toString(),
                        username = user.login,
                        displayName = user.name ?: user.login,
                        avatarUrl = user.avatarUrl,
                        authMethod = AuthMethod.PAT,
                        accessToken = clean
                    )
                    authVault.saveAccount(profile, makeActive = true)
                    reloadAccounts()
                    _statusBanner.value = "Connected as @${user.login} via PAT."
                    refreshRepos()
                },
                onFailure = { _statusBanner.value = "PAT sign-in failed: ${it.message}" }
            )
        }
    }

    fun startDeviceFlow(clientId: String, isGitHubApp: Boolean = false) {
        viewModelScope.launch {
            val cleanId = clientId.trim()
            if (cleanId.isEmpty()) {
                _statusBanner.value = "Enter your GitHub App or OAuth App Client ID first."
                return@launch
            }
            deviceFlowAuth.requestDeviceCode(cleanId).fold(
                onSuccess = { codeResp ->
                    _deviceCode.value = codeResp
                    _statusBanner.value = "Enter code ${codeResp.userCode} at ${codeResp.verificationUri}"
                    deviceFlowAuth.pollForToken(cleanId, codeResp.deviceCode, codeResp.interval).fold(
                        onSuccess = { tokenResp ->
                            val token = tokenResp.accessToken ?: return@fold
                            val tempClient = GitHubApiClient { token }
                            val user = tempClient.getAuthenticatedUser().getOrNull()
                            val profile = AccountProfile(
                                id = UUID.randomUUID().toString(),
                                username = user?.login ?: "github-user",
                                displayName = user?.name ?: user?.login ?: "GitHub User",
                                avatarUrl = user?.avatarUrl.orEmpty(),
                                authMethod = if (isGitHubApp) AuthMethod.GITHUB_APP else AuthMethod.DEVICE_FLOW,
                                accessToken = token,
                                refreshToken = tokenResp.refreshToken.orEmpty(),
                                expiresAtEpochSec = tokenResp.expiresIn?.let { System.currentTimeMillis() / 1000 + it } ?: 0L,
                                clientId = cleanId
                            )
                            authVault.saveAccount(profile, makeActive = true)
                            _deviceCode.value = null
                            reloadAccounts()
                            _statusBanner.value = "Connected as @${profile.username} via ${profile.authMethod.displayName}."
                            refreshRepos()
                        },
                        onFailure = {
                            _deviceCode.value = null
                            _statusBanner.value = "Device Flow polling stopped: ${it.message}"
                        }
                    )
                },
                onFailure = { _statusBanner.value = "Device Flow error: ${it.message}" }
            )
        }
    }

    fun generateSshKey(label: String): AccountProfile {
        val profile = authVault.generateSshKeyProfile(label.ifBlank { "android" })
        reloadAccounts()
        _statusBanner.value = "Generated SSH key '${profile.displayName}'. Copy the public key into GitHub Settings."
        return profile
    }

    fun handleOAuthCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        _statusBanner.value = when {
            error != null -> "OAuth cancelled or failed: $error"
            code != null -> "OAuth code received (${code.take(6)}...). Configure your Phase 5 Relay Server URL to exchange it."
            else -> null
        }
    }

    fun switchAccount(accountId: String) {
        authVault.setActiveAccount(accountId)
        reloadAccounts()
        if (_activeAccount.value?.authMethod?.supportsApi == true) refreshRepos()
    }

    fun removeAccount(accountId: String) {
        authVault.removeAccount(accountId)
        reloadAccounts()
    }

    private fun reloadAccounts() {
        _accounts.value = authVault.listAccounts()
        _activeAccount.value = authVault.getActiveAccount()
    }

    // --- Repos & One-Button Local Folder Push ---

    fun refreshRepos() {
        viewModelScope.launch {
            apiClient.listRepos().fold(
                onSuccess = { list ->
                    _repos.value = list
                    if (_selectedRepo.value == null && list.isNotEmpty()) {
                        selectRepo(list.first())
                    }
                },
                onFailure = { _statusBanner.value = "Could not load repos: ${it.message}" }
            )
        }
    }

    fun createRepo(name: String, description: String, isPrivate: Boolean) {
        viewModelScope.launch {
            apiClient.createRepo(name.trim(), description.trim(), isPrivate).fold(
                onSuccess = { repo ->
                    _repos.value = listOf(repo) + _repos.value
                    _selectedRepo.value = repo
                    _statusBanner.value = "Created repository ${repo.fullName}. Pick a folder and tap Push!"
                },
                onFailure = { _statusBanner.value = "Create repo failed: ${it.message}" }
            )
        }
    }

    fun runOneButtonFolderPush(commitMessage: String, autoInjectApkWorkflow: Boolean) {
        val folder = _selectedFolderUri.value ?: run {
            _statusBanner.value = "Select a local folder from your phone first."
            return
        }
        val repo = _selectedRepo.value ?: run {
            _statusBanner.value = "Create or select a target GitHub repository first."
            return
        }
        val account = _activeAccount.value ?: run {
            _statusBanner.value = "Connect your GitHub account in the Auth tab first."
            return
        }

        viewModelScope.launch {
            orchestrator.executePushAndWatchApk(
                folderUri = folder,
                repo = repo,
                commitMessage = commitMessage,
                username = account.username,
                token = account.accessToken,
                autoInjectApkWorkflow = autoInjectApkWorkflow
            )
        }
    }

    fun resetPipeline() = orchestrator.reset()

    fun installApk(apkFile: File) {
        apkManager.launchApkInstaller(apkFile).onFailure {
            _statusBanner.value = it.message
        }
    }

    // --- Workflows, Logs & APK Artifacts ---

    fun refreshWorkflowRuns(repo: GitHubRepo? = _selectedRepo.value) {
        val target = repo ?: return
        viewModelScope.launch {
            val owner = target.owner.login.ifBlank { target.fullName.substringBefore("/") }
            apiClient.listWorkflowRuns(owner, target.name).fold(
                onSuccess = { _workflowRuns.value = it },
                onFailure = { _statusBanner.value = "Workflow runs error: ${it.message}" }
            )
        }
    }

    fun inspectWorkflowRun(run: WorkflowRun) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.listJobsForRun(owner, repo.name, run.id).onSuccess { _runJobs.value = it }
            apiClient.listRunArtifacts(owner, repo.name, run.id).onSuccess { _runArtifacts.value = it }
        }
    }

    fun fetchJobLogs(jobId: Long) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.downloadJobLogs(owner, repo.name, jobId).fold(
                onSuccess = { _jobLogText.value = it.takeLast(12_000) },
                onFailure = { _statusBanner.value = "Log download failed: ${it.message}" }
            )
        }
    }

    fun rerunWorkflow(runId: Long) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.rerunWorkflow(owner, repo.name, runId).fold(
                onSuccess = {
                    _statusBanner.value = "Workflow re-run triggered."
                    refreshWorkflowRuns(repo)
                },
                onFailure = { _statusBanner.value = "Re-run failed: ${it.message}" }
            )
        }
    }

    fun downloadAndInstallArtifact(artifact: WorkflowArtifact) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            _apkDownloadProgress.value = 0f
            apkManager.downloadAndExtractApk(
                owner = owner,
                repo = repo.name,
                artifactId = artifact.id,
                artifactName = artifact.name,
                onProgress = { _apkDownloadProgress.value = it }
            ).fold(
                onSuccess = { apks ->
                    _apkDownloadProgress.value = null
                    _selectedReleaseApkFile.value = apks.first()
                    _statusBanner.value = "Extracted ${apks.first().name}. Launching Android installer..."
                    installApk(apks.first())
                },
                onFailure = {
                    _apkDownloadProgress.value = null
                    _statusBanner.value = "APK error: ${it.message}"
                }
            )
        }
    }

    // --- Pull Requests, Syntax Diffs & Release APK Publishing ---

    fun refreshPrsAndReleases(repo: GitHubRepo? = _selectedRepo.value) {
        val target = repo ?: return
        val owner = target.owner.login.ifBlank { target.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.listBranches(owner, target.name).onSuccess { _repoBranches.value = it }
            apiClient.listPullRequests(owner, target.name).onSuccess { _pullRequests.value = it }
            apiClient.listIssues(owner, target.name).onSuccess { _issues.value = it }
            apiClient.listReleases(owner, target.name).onSuccess { _releases.value = it }
        }
    }

    fun createPullRequest(title: String, head: String, base: String, body: String, draft: Boolean) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.createPullRequest(owner, repo.name, title, head, base, body, draft).fold(
                onSuccess = { pr ->
                    _pullRequests.value = listOf(pr) + _pullRequests.value
                    _statusBanner.value = "Created PR #${pr.number}: ${pr.title}"
                },
                onFailure = { _statusBanner.value = "Create PR failed: ${it.message}" }
            )
        }
    }

    fun openPullRequestDiff(pr: PullRequestItem) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        _selectedPr.value = pr
        _prDiffFiles.value = emptyList()
        viewModelScope.launch {
            apiClient.listPullRequestFiles(owner, repo.name, pr.number).fold(
                onSuccess = { _prDiffFiles.value = it },
                onFailure = { _statusBanner.value = "Diff load failed: ${it.message}" }
            )
        }
    }

    fun closePullRequestDiff() {
        _selectedPr.value = null
        _prDiffFiles.value = emptyList()
    }

    fun mergePullRequest(pullNumber: Int, mergeMethod: String) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.mergePullRequest(owner, repo.name, pullNumber, mergeMethod).fold(
                onSuccess = {
                    _statusBanner.value = "Merged PR #$pullNumber ($mergeMethod)."
                    closePullRequestDiff()
                    refreshPrsAndReleases(repo)
                },
                onFailure = { _statusBanner.value = "Merge failed: ${it.message}" }
            )
        }
    }

    fun selectReleaseApkFromUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    var fileName = "app-release.apk"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIdx >= 0) {
                            fileName = cursor.getString(nameIdx)
                        }
                    }
                    val dest = File(File(getApplication<Application>().cacheDir, "release_uploads").apply { mkdirs() }, fileName)
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    } ?: error("Unable to open selected APK file.")
                    dest
                }
            }.fold(
                onSuccess = { file ->
                    _selectedReleaseApkFile.value = file
                    _statusBanner.value = "Selected ${file.name} for Release upload."
                },
                onFailure = { _statusBanner.value = "Could not load APK: ${it.message}" }
            )
        }
    }

    fun publishReleaseWithApk(
        tagName: String,
        title: String,
        notes: String,
        draft: Boolean,
        prerelease: Boolean
    ) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            _isPublishingRelease.value = true
            apiClient.createRelease(
                owner = owner,
                repo = repo.name,
                tagName = tagName.trim(),
                name = title.trim().ifBlank { tagName.trim() },
                body = notes,
                draft = draft,
                prerelease = prerelease,
                targetCommitish = repo.defaultBranch
            ).fold(
                onSuccess = { release ->
                    val apk = _selectedReleaseApkFile.value
                    if (apk != null && apk.exists()) {
                        apiClient.uploadReleaseApkAsset(release.uploadUrl, apk).fold(
                            onSuccess = { asset ->
                                _statusBanner.value = "Published ${release.tagName} with APK asset '${asset.name}'!"
                            },
                            onFailure = { err ->
                                _statusBanner.value = "Release created, but APK upload failed: ${err.message}"
                            }
                        )
                    } else {
                        _statusBanner.value = "Published GitHub Release ${release.tagName}."
                    }
                    refreshPrsAndReleases(repo)
                },
                onFailure = { _statusBanner.value = "Release creation failed: ${it.message}" }
            )
            _isPublishingRelease.value = false
        }
    }

    // --- Repo File Browser, Build File Editor & Local Git ---

    fun loadRepoContents(repo: GitHubRepo, path: String) {
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.listContents(owner, repo.name, path).onSuccess { _repoContents.value = it }
        }
    }

    fun openFileForEditing(path: String) {
        val repo = _selectedRepo.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.getFileContent(owner, repo.name, path).fold(
                onSuccess = { _editingFile.value = it },
                onFailure = { _statusBanner.value = "Read file failed: ${it.message}" }
            )
        }
    }

    fun closeFileEditor() { _editingFile.value = null }

    fun saveEditedFile(newContent: String, commitMessage: String) {
        val repo = _selectedRepo.value ?: return
        val current = _editingFile.value ?: return
        val owner = repo.owner.login.ifBlank { repo.fullName.substringBefore("/") }
        viewModelScope.launch {
            apiClient.commitFileContent(
                owner = owner,
                repo = repo.name,
                path = current.first.path,
                textContent = newContent,
                commitMessage = commitMessage.ifBlank { "Update ${current.first.name} from Grude" },
                sha = current.first.sha,
                branch = repo.defaultBranch
            ).fold(
                onSuccess = {
                    _editingFile.value = null
                    _statusBanner.value = "Committed ${current.first.path} to ${repo.defaultBranch}."
                    refreshWorkflowRuns(repo)
                },
                onFailure = { _statusBanner.value = "Commit failed: ${it.message}" }
            )
        }
    }

    fun cloneSelectedRepo() {
        val repo = _selectedRepo.value ?: return
        val account = _activeAccount.value ?: return
        viewModelScope.launch {
            _statusBanner.value = "Cloning ${repo.fullName}..."
            gitEngine.cloneRepo(repo.cloneUrl, repo.fullName, account.username, account.accessToken).fold(
                onSuccess = {
                    _statusBanner.value = "Cloned ${repo.fullName} locally."
                    refreshLocalStatus(repo)
                },
                onFailure = { _statusBanner.value = "Clone failed: ${it.message}" }
            )
        }
    }

    fun pullSelectedRepo() {
        val repo = _selectedRepo.value ?: return
        val account = _activeAccount.value ?: return
        viewModelScope.launch {
            gitEngine.pullRepo(repo.fullName, account.username, account.accessToken).fold(
                onSuccess = { msg ->
                    _statusBanner.value = msg
                    refreshLocalStatus(repo)
                },
                onFailure = { _statusBanner.value = "Pull failed: ${it.message}" }
            )
        }
    }

    fun commitAndPushLocal(commitMessage: String) {
        val repo = _selectedRepo.value ?: return
        val account = _activeAccount.value ?: return
        viewModelScope.launch {
            gitEngine.commitAndPush(repo.fullName, commitMessage, account.username, account.accessToken).fold(
                onSuccess = { sha ->
                    _statusBanner.value = "Pushed local commit $sha."
                    refreshLocalStatus(repo)
                    refreshWorkflowRuns(repo)
                },
                onFailure = { _statusBanner.value = "Push failed: ${it.message}" }
            )
        }
    }

    private fun refreshLocalStatus(repo: GitHubRepo) {
        viewModelScope.launch {
            _localGitStatus.value = gitEngine.getStatus(repo.fullName).getOrNull()
        }
    }
}
