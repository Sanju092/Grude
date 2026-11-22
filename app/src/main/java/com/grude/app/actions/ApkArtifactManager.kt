package com.grude.app.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.grude.app.api.GitHubApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Downloads GitHub Actions .zip artifacts, safely extracts .apk files on-device,
 * and launches Android's native PackageInstaller via FileProvider.
 */
class ApkArtifactManager(
    private val context: Context,
    private val apiClient: GitHubApiClient
) {
    private val apksDir: File
        get() = File(context.cacheDir, "apks").apply { mkdirs() }

    suspend fun downloadAndExtractApk(
        owner: String,
        repo: String,
        artifactId: Long,
        artifactName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = artifactName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val zipFile = File(apksDir, "${safeName}_$artifactId.zip")

            apiClient.downloadArtifactZip(
                owner = owner,
                repo = repo,
                artifactId = artifactId,
                destinationZip = zipFile,
                onProgress = onProgress
            ).getOrThrow()

            val extractDir = File(apksDir, "extracted_${artifactId}").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            val extractedApks = mutableListOf<File>()
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(extractDir, entry.name)
                        // Prevent Zip Slip path traversal
                        val canonicalDest = extractDir.canonicalPath
                        val canonicalOut = outFile.canonicalPath
                        if (!canonicalOut.startsWith(canonicalDest + File.separator)) {
                            error("Unsafe zip entry blocked: ${entry.name}")
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        if (outFile.name.endsWith(".apk", ignoreCase = true)) {
                            extractedApks.add(outFile)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zipFile.delete()
            if (extractedApks.isEmpty()) {
                error("Artifact downloaded and unzipped, but no .apk file was found inside.")
            }
            extractedApks
        }
    }

    /**
     * Launches Android's system APK installer via FileProvider.
     * If "Install unknown apps" permission is not granted yet on Android 8+, opens the exact settings toggle.
     */
    fun launchApkInstaller(apkFile: File): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            error("Please allow Grude to install unknown apps, then tap Install APK again.")
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
