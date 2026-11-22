package com.grude.app.git

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.io.FileOutputStream

data class LocalRepoStatus(
    val branch: String,
    val modified: Set<String>,
    val untracked: Set<String>,
    val added: Set<String>
)

/**
 * Pure-JVM on-device Git engine powered by Eclipse JGit.
 * Handles SAF local folder -> single-button push (with optional workflow YAML injection),
 * plus standard clone, pull, commit, push, branch switching, and status inspection.
 */
class LocalGitEngine(private val context: Context) {

    private val workspacesDir: File
        get() = File(context.filesDir, "grude_repos").apply { mkdirs() }

    private val ignoredFolderNames = setOf(".git", ".gradle", "build", ".idea", "node_modules")

    fun repoDirFor(repoFullName: String): File {
        val safeName = repoFullName.replace("/", "__")
        return File(workspacesDir, safeName)
    }

    /**
     * Single-button folder push:
     * 1. Copies files from the selected Android SAF folder URI into the app's local repo workspace.
     * 2. Optionally injects .github/workflows/build-apk.yml if no workflow file exists.
     * 3. Initializes Git, stages all files, commits, sets remote origin, and force/fast-forward pushes.
     */
    suspend fun pushLocalFolderToRepo(
        folderTreeUri: Uri,
        remoteHttpsUrl: String,
        repoFullName: String,
        branchName: String = "main",
        commitMessage: String = "Initial push from Grude",
        username: String,
        token: String,
        autoInjectApkWorkflow: Boolean = true,
        onStepUpdate: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceTree = DocumentFile.fromTreeUri(context, folderTreeUri)
                ?: error("Could not read selected folder. Please grant folder access again.")

            val targetDir = repoDirFor(repoFullName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            onStepUpdate("Copying files from mobile folder...")
            val copiedCount = copyDocumentTree(sourceTree, targetDir)
            if (copiedCount == 0) error("Selected folder contains no files to push.")

            var injectedWorkflow = false
            if (autoInjectApkWorkflow) {
                val workflowsDir = File(targetDir, ".github/workflows")
                val hasExistingWorkflow = workflowsDir.exists() &&
                    (workflowsDir.listFiles()?.any { it.name.endsWith(".yml") || it.name.endsWith(".yaml") } == true)
                if (!hasExistingWorkflow) {
                    onStepUpdate("Injecting .github/workflows/build-apk.yml...")
                    val wfFile = File(targetDir, WorkflowTemplate.DEFAULT_WORKFLOW_RELATIVE_PATH)
                    wfFile.parentFile?.mkdirs()
                    wfFile.writeText(WorkflowTemplate.ANDROID_APK_WORKFLOW_YAML)
                    injectedWorkflow = true
                }
            }

            onStepUpdate("Initializing Git repository & staging $copiedCount files...")
            Git.init().setDirectory(targetDir).setInitialBranch(branchName).call().use { git ->
                git.add().addFilepattern(".").call()
                val commit = git.commit()
                    .setMessage(commitMessage)
                    .setAuthor(username.ifBlank { "Grude User" }, "$username@users.noreply.github.com")
                    .call()

                git.remoteRemove().setRemoteName("origin").call()
                git.remoteAdd()
                    .setName("origin")
                    .setUri(URIish(remoteHttpsUrl))
                    .call()

                onStepUpdate("Pushing to $repoFullName ($branchName)...")
                val creds = UsernamePasswordCredentialsProvider(username.ifBlank { "x-access-token" }, token)
                git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(creds)
                    .setForce(true)
                    .call()

                val workflowNote = if (injectedWorkflow) " + auto-injected build-apk.yml" else ""
                onStepUpdate("Pushed $copiedCount files$workflowNote (${commit.name.take(7)})")
                commit.name
            }
        }
    }

    suspend fun cloneRepo(
        remoteHttpsUrl: String,
        repoFullName: String,
        username: String,
        token: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = repoDirFor(repoFullName)
            if (targetDir.exists()) targetDir.deleteRecursively()
            val creds = UsernamePasswordCredentialsProvider(username.ifBlank { "x-access-token" }, token)
            Git.cloneRepository()
                .setURI(remoteHttpsUrl)
                .setDirectory(targetDir)
                .setCredentialsProvider(creds)
                .call()
                .close()
            targetDir
        }
    }

    suspend fun pullRepo(repoFullName: String, username: String, token: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetDir = repoDirFor(repoFullName)
                require(targetDir.exists()) { "Repository is not cloned locally yet." }
                val creds = UsernamePasswordCredentialsProvider(username.ifBlank { "x-access-token" }, token)
                Git.open(targetDir).use { git ->
                    val res = git.pull().setCredentialsProvider(creds).call()
                    if (res.isSuccessful) "Pulled latest changes cleanly." else "Pull finished with conflicts."
                }
            }
        }

    suspend fun commitAndPush(
        repoFullName: String,
        commitMessage: String,
        username: String,
        token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = repoDirFor(repoFullName)
            require(targetDir.exists()) { "Repository is not cloned locally yet." }
            val creds = UsernamePasswordCredentialsProvider(username.ifBlank { "x-access-token" }, token)
            Git.open(targetDir).use { git ->
                git.add().addFilepattern(".").call()
                val commit = git.commit()
                    .setMessage(commitMessage)
                    .setAuthor(username.ifBlank { "Grude User" }, "$username@users.noreply.github.com")
                    .call()
                git.push().setCredentialsProvider(creds).call()
                commit.name.take(7)
            }
        }
    }

    suspend fun listBranches(repoFullName: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = repoDirFor(repoFullName)
            require(targetDir.exists()) { "Repository is not cloned locally yet." }
            Git.open(targetDir).use { git ->
                git.branchList().call().map { it.name.removePrefix("refs/heads/") }
            }
        }
    }

    suspend fun checkoutBranch(repoFullName: String, branchName: String, createNew: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetDir = repoDirFor(repoFullName)
                Git.open(targetDir).use { git ->
                    git.checkout().setCreateBranch(createNew).setName(branchName).call()
                    Unit
                }
            }
        }

    suspend fun getStatus(repoFullName: String): Result<LocalRepoStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = repoDirFor(repoFullName)
            require(targetDir.exists()) { "Repository is not cloned locally yet." }
            Git.open(targetDir).use { git ->
                val status = git.status().call()
                LocalRepoStatus(
                    branch = git.repository.branch ?: "main",
                    modified = status.modified,
                    untracked = status.untracked,
                    added = status.added
                )
            }
        }
    }

    private fun copyDocumentTree(source: DocumentFile, destination: File): Int {
        var count = 0
        for (child in source.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (name in ignoredFolderNames) continue
                val subDir = File(destination, name).apply { mkdirs() }
                count += copyDocumentTree(child, subDir)
            } else if (child.isFile) {
                val outFile = File(destination, name)
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                count++
            }
        }
        return count
    }
}
