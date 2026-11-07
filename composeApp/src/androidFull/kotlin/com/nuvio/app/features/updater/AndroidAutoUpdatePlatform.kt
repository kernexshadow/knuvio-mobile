package com.nuvio.app.features.updater

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.nuvio.app.core.build.AppVersionConfig

/** knuvio: background downloads and automatic installs on Android, registered by [AndroidAppUpdaterPlatform]. */
internal class AndroidAutoUpdatePlatform(private val context: Context) : AutoUpdatePlatform {

    override val isAutoInstallSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Debug builds never check for updates, so they schedule no background work either.
    private val isDebuggable: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun isAutoInstallEnabled(): Boolean = AutoUpdatePreferences.isAutoInstallEnabled(context)

    override fun setAutoInstallEnabled(enabled: Boolean) {
        AutoUpdatePreferences.setAutoInstallEnabled(context, enabled)
        syncBackgroundJobs()
        if (enabled && !isDebuggable && UpdateDownloadStore.pendingInstall(context) != null) {
            UpdateJobService.scheduleIdleInstall(context)
        }
    }

    override fun isAutoInstallNoticeEnabled(): Boolean = AutoUpdatePreferences.isAutoInstallNoticeEnabled(context)

    override fun setAutoInstallNoticeEnabled(enabled: Boolean) {
        AutoUpdatePreferences.setAutoInstallNoticeEnabled(context, enabled)
    }

    override fun downloadedApkPath(update: AppUpdate): String? =
        UpdateDownloadStore.downloadedFileFor(context, update)?.absolutePath

    override suspend fun download(
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = UpdateDownloadStore.download(context, update, onProgress).map { it.absolutePath }

    override fun onNewerReleaseFound() {
        if (!isDebuggable && isAutoInstallEnabled()) UpdateJobService.scheduleCheckNow(context)
    }

    override fun clearDownloads() {
        UpdateJobService.cancelIdleInstall(context)
        UpdateDownloadStore.clear(context)
    }

    override fun onLaunch(): String? {
        UpdateDownloadStore.clearIfInstalled(context)
        syncBackgroundJobs()
        return consumeAutoInstalledTag()
    }

    // Shows the "updated" banner once after the idle job installed a release, then forgets it.
    private fun consumeAutoInstalledTag(): String? {
        val tag = AutoUpdatePreferences.getAutoInstalledTag(context) ?: return null
        // Still newer than the installed version means the install has not happened yet.
        if (VersionUtils.isRemoteNewer(tag, AppVersionConfig.VERSION_NAME)) return null
        AutoUpdatePreferences.setAutoInstalledTag(context, null)
        if (VersionUtils.isRemoteNewer(AppVersionConfig.VERSION_NAME, tag)) return null
        return tag.takeIf { isAutoInstallNoticeEnabled() }
    }

    // Background checks and the idle install job run only while automatic installs are on.
    private fun syncBackgroundJobs() {
        if (isDebuggable) return
        if (isAutoInstallEnabled()) {
            UpdateJobService.schedulePeriodicCheck(context)
        } else {
            UpdateJobService.cancelPeriodicCheck(context)
            UpdateJobService.cancelIdleInstall(context)
        }
    }
}
