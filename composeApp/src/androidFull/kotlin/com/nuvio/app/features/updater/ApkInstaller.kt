package com.nuvio.app.features.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread

private const val TAG = "UpdateInstall"

/** knuvio: installs updates through a PackageInstaller session that carries the APK's own dex metadata. */
internal object ApkInstaller {

    /**
     * Installs [apkFile] as an update of this app through a PackageInstaller session, adding the APK's own dex
     * metadata so the new version is compiled at install time. A [background] install asks the system to skip the
     * confirmation screen, which Android 12+ allows for an app updating itself; elsewhere the system still asks.
     * The outcome arrives in [UpdateInstallResultReceiver].
     */
    fun install(
        context: Context,
        apkFile: File,
        background: Boolean,
        withDexMetadata: Boolean = true,
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Sessions default to internal-only, which the system rejects for an app the user moved to adopted
            // storage. Auto keeps the update on the volume the app is already on.
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            if (background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val dexMetadata = if (withDexMetadata) {
            UpdateDexMetadata.extract(apkFile, Build.VERSION.SDK_INT, File(apkFile.parentFile, "update.dm"))
        } else {
            null
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.writeFile("base.apk", apkFile)
                dexMetadata?.let { session.writeFile("base.dm", it) }
                session.commit(
                    UpdateInstallResultReceiver.statusReceiver(
                        context = context,
                        sessionId = sessionId,
                        apkFile = apkFile,
                        background = background,
                        withDexMetadata = dexMetadata != null,
                    ).intentSender,
                )
            }
        } catch (e: Exception) {
            packageInstaller.abandonSession(sessionId)
            throw e
        } finally {
            dexMetadata?.delete()
        }
    }

    /**
     * Installs an update the user asked for. Writing the APK into the session takes a moment, so it runs off the main
     * thread; when the session cannot be created, the system installer screen takes over.
     */
    fun installForUser(context: Context, apkFile: File) {
        thread(name = "update-install") {
            runCatching { install(context, apkFile, background = false) }
                .onFailure { error ->
                    Log.w(TAG, "Session install failed, falling back to the system installer", error)
                    runCatching { launchSystemInstaller(context, apkFile) }
                        .onFailure { Log.w(TAG, "System installer could not be opened", it) }
                }
        }
    }

    /** Hands the APK to the system installer screen, used when the session install cannot be completed. */
    fun launchSystemInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private fun PackageInstaller.Session.writeFile(name: String, file: File) {
        file.inputStream().use { input ->
            openWrite(name, 0, file.length()).use { output ->
                input.copyTo(output)
                fsync(output)
            }
        }
    }
}
