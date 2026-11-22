package com.grude.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AuthMethod(val displayName: String, val supportsApi: Boolean, val supportsGit: Boolean) {
    PAT("Personal Access Token", supportsApi = true, supportsGit = true),
    DEVICE_FLOW("Device Flow (Serverless)", supportsApi = true, supportsGit = true),
    GITHUB_APP("GitHub App (Fine-Grained)", supportsApi = true, supportsGit = true),
    OAUTH_WEB("OAuth App (Web Flow)", supportsApi = true, supportsGit = true),
    SSH_KEY("SSH Key Pair (Git Transport)", supportsApi = false, supportsGit = true)
}

@Serializable
data class AccountProfile(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarUrl: String = "",
    val authMethod: AuthMethod,
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochSec: Long = 0L,
    val clientId: String = "",
    val relayServerUrl: String = "",
    val sshPublicKey: String = "",
    val sshPrivateKeyPem: String = ""
)

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val description: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("clone_url") val cloneUrl: String = "",
    @SerialName("ssh_url") val sshUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val owner: GitHubOwner = GitHubOwner("")
)

@Serializable
data class GitHubOwner(
    val login: String
)

@Serializable
data class BranchItem(
    val name: String,
    val protected: Boolean = false
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String = "",
    val private: Boolean = true,
    @SerialName("auto_init") val autoInit: Boolean = false
)

@Serializable
data class RepoContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val type: String, // "file" or "dir"
    val size: Long = 0L,
    val content: String? = null,
    val encoding: String? = null
)

@Serializable
data class UpdateFileRequest(
    val message: String,
    val content: String, // Base64 encoded
    val sha: String? = null,
    val branch: String? = null
)

@Serializable
data class WorkflowsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val workflows: List<WorkflowSummary> = emptyList()
)

@Serializable
data class WorkflowSummary(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String = "",
    val status: String = "", // queued, in_progress, completed
    val conclusion: String? = null, // success, failure, cancelled
    @SerialName("head_branch") val headBranch: String = "",
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class WorkflowJobsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<WorkflowJob> = emptyList()
)

@Serializable
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val steps: List<WorkflowStep> = emptyList()
)

@Serializable
data class WorkflowStep(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int
)

@Serializable
data class ArtifactsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val artifacts: List<WorkflowArtifact> = emptyList()
)

@Serializable
data class WorkflowArtifact(
    val id: Long,
    val name: String,
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0L,
    val expired: Boolean = false,
    @SerialName("archive_download_url") val archiveDownloadUrl: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

// --- Pull Requests & Diffs ---

@Serializable
data class PullRequestBranchRef(
    val ref: String = "",
    val sha: String = ""
)

@Serializable
data class PullRequestItem(
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    val draft: Boolean = false,
    val user: GitHubOwner = GitHubOwner(""),
    val head: PullRequestBranchRef = PullRequestBranchRef(),
    val base: PullRequestBranchRef = PullRequestBranchRef(),
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class CreatePullRequestBody(
    val title: String,
    val head: String,
    val base: String,
    val body: String = "",
    val draft: Boolean = false
)

@Serializable
data class PullRequestFile(
    val sha: String = "",
    val filename: String,
    val status: String = "modified", // added, removed, modified, renamed
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null
)

@Serializable
data class MergePullRequestBody(
    @SerialName("merge_method") val mergeMethod: String = "squash", // merge, squash, rebase
    @SerialName("commit_title") val commitTitle: String? = null
)

// --- Issues ---

@Serializable
data class IssueItem(
    val number: Int,
    val title: String,
    val state: String,
    val user: GitHubOwner = GitHubOwner(""),
    @SerialName("html_url") val htmlUrl: String = ""
)

// --- Releases & Release APK Assets ---

@Serializable
data class GitHubRelease(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("upload_url") val uploadUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<ReleaseAsset> = emptyList()
)

@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long = 0L,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Serializable
data class CreateReleaseBody(
    @SerialName("tag_name") val tagName: String,
    val name: String,
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("target_commitish") val targetCommitish: String? = null
)

// --- Device Flow & OAuth ---

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)
