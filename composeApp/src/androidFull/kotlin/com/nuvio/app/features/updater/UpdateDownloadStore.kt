package com.nuvio.app.features.updater

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_download_failed_http
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val PREFERENCES_NAME = "nuvio_updater"

/** knuvio: settings of the automatic installs, next to upstream's updater preferences. */
internal object AutoUpdatePreferences {
    private const val AUTO_INSTALL_ENABLED = "auto_install_enabled"
    private const val AUTO_INSTALL_NOTICE_ENABLED = "auto_install_notice_enabled"
    private const val AUTO_INSTALLED_TAG = "auto_installed_release_tag"

    fun isAutoInstallEnabled(context: Context): Boolean = prefs(context).getBoolean(AUTO_INSTALL_ENABLED, false)

    fun setAutoInstallEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AUTO_INSTALL_ENABLED, enabled).apply()
    }

    fun isAutoInstallNoticeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(AUTO_INSTALL_NOTICE_ENABLED, true)

    fun setAutoInstallNoticeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AUTO_INSTALL_NOTICE_ENABLED, enabled).apply()
    }

    /** Release the idle job last installed without asking, so the next launch can say the app was updated. */
    fun getAutoInstalledTag(context: Context): String? = prefs(context).getString(AUTO_INSTALLED_TAG, null)

    fun setAutoInstalledTag(context: Context, tag: String?) {
        prefs(context).edit().apply {
            if (tag == null) remove(AUTO_INSTALLED_TAG) else putString(AUTO_INSTALLED_TAG, tag)
        }.commit()
    }

    internal fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

/**
 * knuvio: keeps the downloaded update APK in app storage and remembers which release it belongs to, so a download made
 * in the background can be installed later, from the banner or by the idle install job.
 */
internal object UpdateDownloadStore {
    private const val DOWNLOADED_TAG = "downloaded_release_tag"
    private const val DOWNLOADED_APK_PATH = "downloaded_apk_path"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // The banner and the background job can ask for the same download; the second one waits and reuses the file.
    private val downloadMutex = Mutex()

    // Files storage instead of cache, which the system may clear before the update is installed.
    private fun updatesDir(context: Context): File = File(context.filesDir, "updates")

    /** Returns the already downloaded APK for this release, or null when it is missing or incomplete. */
    fun downloadedFileFor(context: Context, update: AppUpdate): File? {
        val prefs = AutoUpdatePreferences.prefs(context)
        if (prefs.getString(DOWNLOADED_TAG, null) != update.tag) return null
        val file = prefs.getString(DOWNLOADED_APK_PATH, null)?.let(::File) ?: return null
        val expectedSize = update.assetSizeBytes
        return file.takeIf { it.exists() && (expectedSize == null || it.length() == expectedSize) }
    }

    /** Returns the downloaded APK when it is for a version newer than the installed one. */
    fun pendingInstall(context: Context): Pair<String, File>? {
        val prefs = AutoUpdatePreferences.prefs(context)
        val tag = prefs.getString(DOWNLOADED_TAG, null) ?: return null
        val file = prefs.getString(DOWNLOADED_APK_PATH, null)?.let(::File) ?: return null
        if (!file.exists() || !VersionUtils.isRemoteNewer(tag, AppVersionConfig.VERSION_NAME)) return null
        return tag to file
    }

    suspend fun download(
        context: Context,
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<File> = downloadMutex.withLock {
        downloadedFileFor(context, update)?.let { return@withLock Result.success(it) }
        val safeName = update.assetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destination = File(updatesDir(context), safeName)
        downloadTo(update.assetUrl, destination, onProgress).onSuccess { file ->
            updatesDir(context).listFiles()?.filter { it != file }?.forEach { it.deleteRecursively() }
            AutoUpdatePreferences.prefs(context).edit()
                .putString(DOWNLOADED_TAG, update.tag)
                .putString(DOWNLOADED_APK_PATH, file.absolutePath)
                .commit()
        }
    }

    /** Drops the downloaded APK once it is installed, outdated, or no longer wanted. */
    fun clear(context: Context) {
        updatesDir(context).deleteRecursively()
        AutoUpdatePreferences.prefs(context).edit()
            .remove(DOWNLOADED_TAG)
            .remove(DOWNLOADED_APK_PATH)
            .commit()
    }

    fun clearIfInstalled(context: Context) {
        val tag = AutoUpdatePreferences.prefs(context).getString(DOWNLOADED_TAG, null) ?: return
        if (!VersionUtils.isRemoteNewer(tag, AppVersionConfig.VERSION_NAME)) clear(context)
    }

    private suspend fun downloadTo(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            // Written beside the destination and renamed at the end, so an interrupted download is never taken as
            // a complete APK.
            val partial = File(destination.parentFile, destination.name + ".part")
            partial.delete()
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error(getString(Res.string.updates_download_failed_http, response.code))
                val body = response.body
                val totalBytes = body.contentLength().takeIf { it > 0L }
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.fd.sync()
                    }
                }
            }
            destination.delete()
            check(partial.renameTo(destination)) { "Could not move ${partial.name} into place" }
            destination
        }
    }
}
