package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.features.updater.AutoUpdate
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_auto_install_description
import nuvio.composeapp.generated.resources.updates_auto_install_notice_description
import nuvio.composeapp.generated.resources.updates_auto_install_notice_title
import nuvio.composeapp.generated.resources.updates_auto_install_title
import org.jetbrains.compose.resources.stringResource

/** knuvio: automatic update settings, shown where the platform supports them (Android 12+, full flavor). */
@Composable
internal fun AutoUpdateSettingsRows(isTablet: Boolean) {
    if (!AutoUpdate.isSupported) return
    val autoInstallEnabled by AutoUpdate.autoInstallEnabled.collectAsState()
    val noticeEnabled by AutoUpdate.autoInstallNoticeEnabled.collectAsState()

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.updates_auto_install_title),
        description = stringResource(Res.string.updates_auto_install_description),
        checked = autoInstallEnabled,
        isTablet = isTablet,
        onCheckedChange = AutoUpdate::setAutoInstallEnabled,
    )
    if (autoInstallEnabled) {
        SettingsGroupDivider(isTablet = isTablet)
        SettingsSwitchRow(
            title = stringResource(Res.string.updates_auto_install_notice_title),
            description = stringResource(Res.string.updates_auto_install_notice_description),
            checked = noticeEnabled,
            isTablet = isTablet,
            onCheckedChange = AutoUpdate::setAutoInstallNoticeEnabled,
        )
    }
}
