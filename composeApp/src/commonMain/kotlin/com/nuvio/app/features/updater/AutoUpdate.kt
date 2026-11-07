package com.nuvio.app.features.updater

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * knuvio: background downloads and automatic installs of updates. The platform that supports them (the Android full
 * flavor) registers an [AutoUpdatePlatform] at startup. Without one, [AutoUpdate.isSupported] stays false and the
 * updater works as upstream's.
 */
interface AutoUpdatePlatform {
    /** Installing without a confirmation screen needs Android 12+. */
    val isAutoInstallSupported: Boolean

    fun isAutoInstallEnabled(): Boolean

    fun setAutoInstallEnabled(enabled: Boolean)

    fun isAutoInstallNoticeEnabled(): Boolean

    fun setAutoInstallNoticeEnabled(enabled: Boolean)

    /** Path of the APK already downloaded for [update], or null when it is missing or incomplete. */
    fun downloadedApkPath(update: AppUpdate): String?

    /** Downloads [update] where the background jobs find it, or returns the file already downloaded for it. */
    suspend fun download(
        update: AppUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String>

    /** The app found a release newer than the installed one: with automatic installs on, download it now. */
    fun onNewerReleaseFound()

    fun clearDownloads()

    /**
     * Runs once per launch: drops a download that is already installed, schedules the background jobs, and returns the
     * release installed automatically since the last launch when the "updated" banner should show it.
     */
    fun onLaunch(): String?
}

object AutoUpdate {
    var platform: AutoUpdatePlatform? = null
        private set

    private val _autoInstallEnabled = MutableStateFlow(false)
    val autoInstallEnabled: StateFlow<Boolean> = _autoInstallEnabled.asStateFlow()

    private val _autoInstallNoticeEnabled = MutableStateFlow(true)
    val autoInstallNoticeEnabled: StateFlow<Boolean> = _autoInstallNoticeEnabled.asStateFlow()

    val isSupported: Boolean
        get() = platform?.isAutoInstallSupported == true

    fun register(platform: AutoUpdatePlatform) {
        this.platform = platform
        _autoInstallEnabled.value = platform.isAutoInstallEnabled()
        _autoInstallNoticeEnabled.value = platform.isAutoInstallNoticeEnabled()
    }

    fun setAutoInstallEnabled(enabled: Boolean) {
        val platform = platform ?: return
        platform.setAutoInstallEnabled(enabled)
        _autoInstallEnabled.value = enabled
    }

    fun setAutoInstallNoticeEnabled(enabled: Boolean) {
        val platform = platform ?: return
        platform.setAutoInstallNoticeEnabled(enabled)
        _autoInstallNoticeEnabled.value = enabled
    }
}
