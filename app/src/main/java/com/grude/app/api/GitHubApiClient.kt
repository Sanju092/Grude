package com.grude.app.api

import android.util.Base64
import com.grude.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * GitHub REST API v3 client for Grude.
 * Covers Repos, Contents, Branches, Actions (Workflows, Runs, Jobs, Logs, 60s signed Artifact Zips),
 * Pull Requests (list, create, diff files, merge), Issues, and Releases (create + binary APK asset upload).
 */
class GitHubApiClient(
    private val tokenProvider: () -> String?
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val apkMediaType = "application/vnd.android.package-archive".toMediaType()

    private val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient: OkHttpClient = apiClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val transferClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun authRequestBuilder(path: String): Request.Builder {
        val token = tokenProvider() ?: error("No active GitHub API token configured.")
        val url = if (path.startsWith("http")) path else "https://api.github.com$path"
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $token")
    }

    suspend fun getAuthenticatedUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        runCatching {
            apiClient.newCall(authRequestBuilder("/user").get().build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Failed to fetch user (${resp.code}): $raw")
                json.decodeFromString<GitHubUser>(raw)
            }
        }
    }

    suspend fun listRepos(): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = authRequestBuilder("/user/repos?sort=updated&per_page=50").get().build()
            apiClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Failed to list repos (${resp.code}): $raw")
                json.decodeFromString<List<GitHubRepo>>(raw)
            }
        }
    }

    suspend fun createRepo(name: String, description: String, isPrivate: Boolean): Result<GitHubRepo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(
                    CreateRepoRequest(name = name, description = description, private = isPrivate, autoInit = false)
                )
                val req = authRequestBuilder("/user/repos")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Create repo failed (${resp.code}): $raw")
                    json.decodeFromString<GitHubRepo>(raw)
                }
            }
        }

    suspend fun listBranches(owner: String, repo: String): Result<List<BranchItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/branches?per_page=50").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List branches failed (${resp.code}): $raw")
                    json.decodeFromString<List<BranchItem>>(raw)
                }
            }
        }

    suspend fun listContents(owner: String, repo: String, path: String = ""): Result<List<RepoContentItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanPath = path.trimStart('/')
                val endpoint = if (cleanPath.isEmpty()) {
                    "/repos/$owner/$repo/contents"
                } else {
                    "/repos/$owner/$repo/contents/$cleanPath"
                }
                apiClient.newCall(authRequestBuilder(endpoint).get().build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List contents failed (${resp.code}): $raw")
                    json.decodeFromString<List<RepoContentItem>>(raw)
                }
            }
        }

    suspend fun getFileContent(owner: String, repo: String, path: String): Result<Pair<RepoContentItem, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = "/repos/$owner/$repo/contents/${path.trimStart('/')}"
                apiClient.newCall(authRequestBuilder(endpoint).get().build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Read file failed (${resp.code}): $raw")
                    val item = json.decodeFromString<RepoContentItem>(raw)
                    val decoded = item.content
                        ?.replace("\n", "")
                        ?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
                        .orEmpty()
                    item to decoded
                }
            }
        }

    suspend fun commitFileContent(
        owner: String,
        repo: String,
        path: String,
        textContent: String,
        commitMessage: String,
        sha: String? = null,
        branch: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = Base64.encodeToString(textContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = json.encodeToString(
                UpdateFileRequest(message = commitMessage, content = encoded, sha = sha, branch = branch)
            )
            val req = authRequestBuilder("/repos/$owner/$repo/contents/${path.trimStart('/')}")
                .put(payload.toRequestBody(jsonMediaType))
                .build()
            apiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Commit file failed (${resp.code}): ${resp.body?.string().orEmpty()}")
                }
            }
        }
    }

    // --- GitHub Actions: Workflows, Runs, Jobs, Logs, and Artifacts ---

    suspend fun listWorkflows(owner: String, repo: String): Result<List<WorkflowSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/actions/workflows").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List workflows failed (${resp.code}): $raw")
                    json.decodeFromString<WorkflowsResponse>(raw).workflows
                }
            }
        }

    suspend fun listWorkflowRuns(owner: String, repo: String): Result<List<WorkflowRun>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/actions/runs?per_page=25").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List runs failed (${resp.code}): $raw")
                    json.decodeFromString<WorkflowRunsResponse>(raw).workflowRuns
                }
            }
        }

    suspend fun triggerWorkflowDispatch(owner: String, repo: String, workflowId: Long, ref: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = """{"ref":"$ref"}"""
                val req = authRequestBuilder("/repos/$owner/$repo/actions/workflows/$workflowId/dispatches")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()
                apiClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Workflow dispatch failed (${resp.code})")
                }
            }
        }

    suspend fun rerunWorkflow(owner: String, repo: String, runId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/actions/runs/$runId/rerun")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                apiClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Rerun failed (${resp.code})")
                }
            }
        }

    suspend fun listJobsForRun(owner: String, repo: String, runId: Long): Result<List<WorkflowJob>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/actions/runs/$runId/jobs").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List jobs failed (${resp.code}): $raw")
                    json.decodeFromString<WorkflowJobsResponse>(raw).jobs
                }
            }
        }

    suspend fun downloadJobLogs(owner: String, repo: String, jobId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val initialReq = authRequestBuilder("/repos/$owner/$repo/actions/jobs/$jobId/logs").get().build()
                val signedUrl = noRedirectClient.newCall(initialReq).execute().use { resp ->
                    resp.header("Location")
                        ?: if (resp.isSuccessful) return@runCatching resp.body?.string().orEmpty()
                        else error("Job logs unavailable (${resp.code})")
                }
                transferClient.newCall(Request.Builder().url(signedUrl).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("Failed downloading signed logs (${resp.code})")
                    resp.body?.string().orEmpty()
                }
            }
        }

    suspend fun listRunArtifacts(owner: String, repo: String, runId: Long): Result<List<WorkflowArtifact>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/actions/runs/$runId/artifacts").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List artifacts failed (${resp.code}): $raw")
                    json.decodeFromString<ArtifactsResponse>(raw).artifacts
                }
            }
        }

    suspend fun downloadArtifactZip(
        owner: String,
        repo: String,
        artifactId: Long,
        destinationZip: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val redirectReq = authRequestBuilder("/repos/$owner/$repo/actions/artifacts/$artifactId/zip")
                .get()
                .build()

            val signedUrl = noRedirectClient.newCall(redirectReq).execute().use { resp ->
                resp.header("Location")
                    ?: error("Artifact download redirect failed (${resp.code}): ${resp.body?.string().orEmpty()}")
            }

            transferClient.newCall(Request.Builder().url(signedUrl).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) error("Artifact zip stream failed (${resp.code})")
                val body = resp.body ?: error("Empty artifact response body")
                val totalBytes = body.contentLength()
                destinationZip.parentFile?.mkdirs()

                body.byteStream().use { input ->
                    FileOutputStream(destinationZip).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
                onProgress(1f)
                destinationZip
            }
        }
    }

    // --- Pull Requests (List, Create, Diff Files, Merge) ---

    suspend fun listPullRequests(owner: String, repo: String): Result<List<PullRequestItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/pulls?state=open&per_page=30").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List PRs failed (${resp.code}): $raw")
                    json.decodeFromString<List<PullRequestItem>>(raw)
                }
            }
        }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String,
        draft: Boolean
    ): Result<PullRequestItem> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(
                CreatePullRequestBody(title = title, head = head, base = base, body = body, draft = draft)
            )
            val req = authRequestBuilder("/repos/$owner/$repo/pulls")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            apiClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Create PR failed (${resp.code}): $raw")
                json.decodeFromString<PullRequestItem>(raw)
            }
        }
    }

    suspend fun listPullRequestFiles(owner: String, repo: String, pullNumber: Int): Result<List<PullRequestFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/pulls/$pullNumber/files?per_page=50").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Fetch PR diff files failed (${resp.code}): $raw")
                    json.decodeFromString<List<PullRequestFile>>(raw)
                }
            }
        }

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        pullNumber: Int,
        mergeMethod: String = "squash"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(MergePullRequestBody(mergeMethod = mergeMethod))
            val req = authRequestBuilder("/repos/$owner/$repo/pulls/$pullNumber/merge")
                .put(payload.toRequestBody(jsonMediaType))
                .build()
            apiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Merge PR failed (${resp.code}): ${resp.body?.string().orEmpty()}")
            }
        }
    }

    // --- Issues ---

    suspend fun listIssues(owner: String, repo: String): Result<List<IssueItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/issues?state=open&per_page=30").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List issues failed (${resp.code}): $raw")
                    json.decodeFromString<List<IssueItem>>(raw)
                }
            }
        }

    // --- Releases & Release APK Asset Publishing ---

    suspend fun listReleases(owner: String, repo: String): Result<List<GitHubRelease>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = authRequestBuilder("/repos/$owner/$repo/releases?per_page=20").get().build()
                apiClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("List releases failed (${resp.code}): $raw")
                    json.decodeFromString<List<GitHubRelease>>(raw)
                }
            }
        }

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String,
        body: String,
        draft: Boolean,
        prerelease: Boolean,
        targetCommitish: String? = null
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(
                CreateReleaseBody(
                    tagName = tagName,
                    name = name,
                    body = body,
                    draft = draft,
                    prerelease = prerelease,
                    targetCommitish = targetCommitish
                )
            )
            val req = authRequestBuilder("/repos/$owner/$repo/releases")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            apiClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Create release failed (${resp.code}): $raw")
                json.decodeFromString<GitHubRelease>(raw)
            }
        }
    }

    /**
     * Uploads a raw Android .apk binary asset to a GitHub Release using the release's upload_url
     * (e.g. https://uploads.github.com/repos/OWNER/REPO/releases/ID/assets?name=app-release.apk).
     */
    suspend fun uploadReleaseApkAsset(
        uploadUrlTemplate: String,
        apkFile: File
    ): Result<ReleaseAsset> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUploadUrl = uploadUrlTemplate.substringBefore("{")
            val encodedName = URLEncoder.encode(apkFile.name, "UTF-8")
            val fullUrl = "$baseUploadUrl?name=$encodedName"

            val req = authRequestBuilder(fullUrl)
                .post(apkFile.asRequestBody(apkMediaType))
                .build()

            transferClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("APK asset upload failed (${resp.code}): $raw")
                json.decodeFromString<ReleaseAsset>(raw)
            }
        }
    }
}
