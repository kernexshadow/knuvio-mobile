package com.nuvio.app.features.updater

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "UpdateJobs"
private const val JOB_ID_CHECK = 10_020
private const val JOB_ID_INSTALL = 10_021
private const val JOB_ID_CHECK_NOW = 10_022
private const val CHECK_INTERVAL_MS = 12 * 60 * 60_000L

/**
 * knuvio: background update work, while automatic installs are on. The periodic job checks for a new release and
 * downloads it on an unmetered network. On Android 12+ a second job then installs the downloaded update while the
 * device is idle, without a confirmation screen.
 */
class UpdateJobService : JobService() {

    private var jobScope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        // The job can start the app's process without an activity, so the updater has to be set up here.
        AndroidAppUpdaterPlatform.initialize(applicationContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        jobScope = scope
        scope.launch {
            // A failed check, download or install asks for a retry with backoff instead of waiting for the next run.
            var retry = false
            try {
                when (params.jobId) {
                    JOB_ID_CHECK, JOB_ID_CHECK_NOW -> checkAndDownload()
                    JOB_ID_INSTALL -> installDownloaded()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoChannelReleaseException) {
                Log.d(TAG, "No release on the update channel")
            } catch (e: Exception) {
                Log.w(TAG, "Update job ${params.jobId} failed", e)
                retry = true
            } finally {
                jobFinished(params, retry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobScope?.cancel()
        return true // reschedule on stop
    }

    private suspend fun checkAndDownload() {
        val context = applicationContext
        if (!AutoUpdatePreferences.isAutoInstallEnabled(context)) return

        UpdateDownloadStore.clearIfInstalled(context)
        val channel = UpdatePreferences.shared.channel.value
        val update = AppUpdaterRepository.getLatestChannelUpdate(channel).getOrThrow()
        if (!VersionUtils.isRemoteNewer(update.tag, AppVersionConfig.VERSION_NAME)) return
        if (update.tag == AndroidAppUpdaterPlatform.getIgnoredTag()) return

        UpdateDownloadStore.download(context, update).getOrThrow()
        Log.d(TAG, "Downloaded ${update.tag} in the background")
        scheduleIdleInstall(context)
    }

    private fun installDownloaded() {
        val context = applicationContext
        if (!canInstallSilently(context) || !AutoUpdatePreferences.isAutoInstallEnabled(context)) return
        val (tag, apkFile) = UpdateDownloadStore.pendingInstall(context) ?: return
        if (tag == AndroidAppUpdaterPlatform.getIgnoredTag()) return
        Log.d(TAG, "Installing $tag while the device is idle")
        AutoUpdatePreferences.setAutoInstalledTag(context, tag)
        ApkInstaller.install(context, apkFile, background = true)
    }

    companion object {
        /** Silent self-updates need Android 12+ and the "install unknown apps" permission. */
        fun canInstallSilently(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.packageManager.canRequestPackageInstalls()

        fun schedulePeriodicCheck(context: Context) {
            val scheduler = context.jobScheduler()
            if (scheduler.allPendingJobs.any { it.id == JOB_ID_CHECK }) return
            val job = JobInfo.Builder(JOB_ID_CHECK, ComponentName(context, UpdateJobService::class.java))
                // Phones may be on mobile data: background downloads wait for an unmetered network.
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPeriodic(CHECK_INTERVAL_MS)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        /** Downloads a newer release found by the in-app check without waiting for the next periodic run. */
        fun scheduleCheckNow(context: Context) {
            val job = JobInfo.Builder(JOB_ID_CHECK_NOW, ComponentName(context, UpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build()
            context.jobScheduler().schedule(job)
        }

        fun cancelPeriodicCheck(context: Context) {
            context.jobScheduler().cancel(JOB_ID_CHECK)
            context.jobScheduler().cancel(JOB_ID_CHECK_NOW)
        }

        fun scheduleIdleInstall(context: Context) {
            if (!canInstallSilently(context)) return
            val job = JobInfo.Builder(JOB_ID_INSTALL, ComponentName(context, UpdateJobService::class.java))
                .setRequiresDeviceIdle(true)
                .setPersisted(true)
                .build()
            context.jobScheduler().schedule(job)
        }

        fun cancelIdleInstall(context: Context) {
            context.jobScheduler().cancel(JOB_ID_INSTALL)
        }

        private fun Context.jobScheduler(): JobScheduler =
            getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    }
}
