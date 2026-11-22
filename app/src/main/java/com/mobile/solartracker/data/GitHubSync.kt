package com.mobile.solartracker.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal GitHub Contents API client used to push/pull the SQLite files.
 * No third-party HTTP library is available offline, so this uses HttpURLConnection.
 */
class GitHubSyncManager(
    private val context: Context,
    private val owner: String,
    private val repo: String,
    private val branch: String,
    private val token: String,
) {

    private val lastPulledShas = context.getSharedPreferences("github_sync_shas", Context.MODE_PRIVATE)

    data class RemoteFile(val name: String, val path: String, val downloadUrl: String, val size: Long, val sha: String)

    sealed class PushResult { data class Pushed(val name: String) : PushResult(); data class Unchanged(val name: String) : PushResult() }
    sealed class PullResult { data class Pulled(val name: String) : PullResult(); data class Unchanged(val name: String) : PullResult() }

    fun testConnection(): String {
        val (code, body) = request("GET", "/repos/$owner/$repo")
        if (code == 401) throw IllegalStateException("Invalid or expired token")
        if (code == 404) throw IllegalStateException("Repository not found or no access")
        if (code !in 200..299) throw IllegalStateException("GitHub returned HTTP $code")
        val json = JSONObject(body)
        return "Connected: ${json.optString("full_name")} (branch $branch)"
    }

    fun listRemoteDatabases(): List<RemoteFile> {
        val (code, body) = request("GET", "/repos/$owner/$repo/contents/databases?ref=$branch")
        if (code == 404) return emptyList()
        if (code !in 200..299) throw IllegalStateException("GitHub returned HTTP $code: $body")
        val array = JSONArray(body)
        val result = mutableListOf<RemoteFile>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name")
            if (!name.endsWith(".db", ignoreCase = true)) continue
            result.add(
                RemoteFile(
                    name = name,
                    path = obj.optString("path"),
                    downloadUrl = obj.optString("download_url"),
                    size = obj.optLong("size"),
                    sha = obj.optString("sha"),
                )
            )
        }
        return result
    }

    fun pushFile(file: File): PushResult {
        if (file.length() > MAX_FILE_BYTES) {
            throw IllegalStateException("${file.name} is larger than 1 MB; split or shrink it first")
        }
        val path = "databases/${file.name}"
        val (getCode, getBody) = request("GET", "/repos/$owner/$repo/contents/$path?ref=$branch")
        val remoteSha = if (getCode == 200) {
            runCatching { JSONObject(getBody).optString("sha") }.getOrNull()?.takeIf { it.isNotEmpty() }
        } else null

        val bytes = file.readBytes()
        val localSha = gitBlobSha(bytes)

        // No change: skip the upload.
        if (remoteSha != null && remoteSha == localSha) {
            return PushResult.Unchanged(file.name)
        }

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("message", "Update ${file.name}")
            put("content", b64)
            put("branch", branch)
            if (remoteSha != null) put("sha", remoteSha)
        }.toString()

        val (putCode, putBody) = request("PUT", "/repos/$owner/$repo/contents/$path", body)
        if (putCode !in 200..201) {
            throw IllegalStateException("Push failed (HTTP $putCode): $putBody")
        }
        return PushResult.Pushed(file.name)
    }

    fun pullFile(remote: RemoteFile, targetDir: File): PullResult {
        val lastPulled = lastPulledShas.getString(remote.name, null)
        if (lastPulled != null && lastPulled == remote.sha && remote.sha.isNotEmpty()) {
            return PullResult.Unchanged(remote.name)
        }
        val bytes = downloadBytes(remote.downloadUrl)
        val target = File(targetDir, remote.name)
        target.writeBytes(bytes)
        lastPulledShas.edit().putString(remote.name, remote.sha).apply()
        return PullResult.Pulled(remote.name)
    }

    private fun downloadBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            throw IllegalStateException("Download failed (HTTP $code): $err")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        return bytes
    }

    private fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
        val url = URL("https://api.github.com$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        conn.disconnect()
        return code to text
    }

    companion object {
        private const val MAX_FILE_BYTES = 1_000_000L

        fun gitBlobSha(bytes: ByteArray): String {
            val md = java.security.MessageDigest.getInstance("SHA-1")
            md.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            md.update(bytes)
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
