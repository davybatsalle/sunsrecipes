package com.sunrecipes.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class ReleaseInfo(
    val tagName: String,
    val apkUrl: String
) {
    val version: String get() = tagName.removePrefix("v")
}

internal object UpdateChecker {
    private const val latestReleaseUrl = "https://api.github.com/repos/davybatsalle/sunsrecipes/releases/latest"
    private const val apkName = "sunsrecipes.apk"

    suspend fun checkLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SunRecipes-Android")
        }
        connection.useConnection { input ->
            val release = JSONObject(input.bufferedReader().use { it.readText() })
            val assets = release.optJSONArray("assets") ?: error("La release ne contient aucun fichier")
            val apk = (0 until assets.length())
                .asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == apkName }
                ?: error("La release ne contient pas l’APK")
            ReleaseInfo(
                tagName = release.optString("tag_name").ifBlank { error("Version GitHub absente") },
                apkUrl = apk.optString("browser_download_url").ifBlank { error("URL de téléchargement absente") }
            )
        }
    }

    suspend fun downloadAndInstall(context: Context, release: ReleaseInfo) = withContext(Dispatchers.IO) {
        val updatesDirectory = File(context.filesDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDirectory, apkName)
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "SunRecipes-Android")
        }
        connection.useConnection { input ->
            input.use { source -> apkFile.outputStream().use { target -> source.copyTo(target) } }
        }
        apkFile
    }

    fun install(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun isNewer(currentVersion: String, latestVersion: String): Boolean =
        compareVersions(latestVersion, currentVersion) > 0

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        return (0 until maxOf(leftParts.size, rightParts.size)).firstNotNullOfOrNull { index ->
            val difference = (leftParts.getOrElse(index) { 0 } - rightParts.getOrElse(index) { 0 })
            difference.takeIf { it != 0 }
        } ?: 0
    }

    private suspend fun <T> HttpURLConnection.useConnection(block: suspend (java.io.InputStream) -> T): T {
        try {
            if (responseCode !in 200..299) error("GitHub a répondu HTTP $responseCode")
            return block(inputStream)
        } finally {
            disconnect()
        }
    }
}
