/*
 *   Copyright 2025 Benoit Letondor
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.benoitletondor.FinanceTrackerapp.view.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.benoitletondor.FinanceTrackerapp.R
import com.benoitletondor.FinanceTrackerapp.compose.AppWithTopAppBarScaffold
import com.benoitletondor.FinanceTrackerapp.compose.BackButtonBehavior
import com.benoitletondor.FinanceTrackerapp.compose.components.LoadingView
import com.benoitletondor.FinanceTrackerapp.compose.rememberPermissionStateCompat
import com.benoitletondor.FinanceTrackerapp.helper.AppTheme
import com.benoitletondor.FinanceTrackerapp.helper.Logger
import com.benoitletondor.FinanceTrackerapp.helper.launchCollect
import com.benoitletondor.FinanceTrackerapp.view.RatingPopup
import com.benoitletondor.FinanceTrackerapp.view.selectcurrency.SelectCurrencyDialog
import com.benoitletondor.FinanceTrackerapp.view.settings.subviews.ErrorView
import com.benoitletondor.FinanceTrackerapp.view.settings.subviews.Settings
import com.benoitletondor.FinanceTrackerapp.view.settings.subviews.ThemePickerDialog
import com.benoitletondor.FinanceTrackerapp.view.settings.subviews.openRedeemCodeDialog
import com.benoitletondor.FinanceTrackerapp.view.settings.subviews.showLowMoneyWarningAmountPickerDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

@Serializable
object SettingsViewDestination

@Composable
fun SettingsView(
    viewModel: SettingsViewModel = hiltViewModel(),
    navigateUp: () -> Unit,
    navigateToBackupSettings: () -> Unit,
    navigateToPremium: () -> Unit,
) {
    SettingsView(
        stateFlow = viewModel.stateFlow,
        eventFlow = viewModel.eventFlow,
        navigateUp = navigateUp,
        navigateToBackupSettings = navigateToBackupSettings,
        onRetryButtonClicked = viewModel::onRetryButtonPressed,
        onCurrencyChangeClicked = viewModel::onCurrencyChangeClicked,
        onAdjustLowMoneyWarningAmountClicked = viewModel::onAdjustLowMoneyWarningAmountClicked,
        onFirstDayOfWeekChanged = viewModel::onFirstDayOfWeekChanged,
        onPremiumButtonClicked = viewModel::onPremiumButtonClicked,
        onProButtonClicked = viewModel::onProButtonClicked,
        onThemeClicked = viewModel::onThemeClicked,
        onShowCheckedBalanceChanged = viewModel::onShowCheckedBalanceChanged,
        onCloudBackupClicked = viewModel::onCloudBackupClicked,
        onDailyReminderNotificationActivatedChanged = viewModel::onDailyReminderNotificationActivatedChanged,
        onMonthlyReportNotificationActivatedChanged = viewModel::onMonthlyReportNotificationActivatedChanged,
        onRateAppClicked = viewModel::onRateAppClicked,
        onShareAppClicked = viewModel::onShareAppClicked,
        onUpdateNotificationActivatedChanged = viewModel::onUpdateNotificationActivatedChanged,
        onBugReportClicked = viewModel::onBugReportClicked,
        onAppClicked = viewModel::onAppClicked,
        onSubscribeButtonClicked = viewModel::onSubscribeButtonClicked,
        onRedeemCodeButtonClicked =  viewModel::onRedeemCodeButtonClicked,
        onPushPermissionResult = viewModel::onPushPermissionResult,
        onAdjustLowMoneyWarningAmountChanged = viewModel::onAdjustLowMoneyWarningAmountChanged,
        navigateToPremium = navigateToPremium,
        onThemeSelected = viewModel::onThemeSelected,
        onNotificationPermissionDeniedPromptAccepted = viewModel::onNotificationPermissionDeniedPromptAccepted,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SettingsView(
    stateFlow: StateFlow<SettingsViewModel.State>,
    eventFlow: Flow<SettingsViewModel.Event>,
    navigateUp: () -> Unit,
    navigateToBackupSettings: () -> Unit,
    onRetryButtonClicked: () -> Unit,
    onCurrencyChangeClicked: () -> Unit,
    onAdjustLowMoneyWarningAmountClicked: () -> Unit,
    onFirstDayOfWeekChanged: (DayOfWeek) -> Unit,
    onPremiumButtonClicked: () -> Unit,
    onProButtonClicked: () -> Unit,
    onThemeClicked: () -> Unit,
    onShowCheckedBalanceChanged: (Boolean) -> Unit,
    onCloudBackupClicked: () -> Unit,
    onDailyReminderNotificationActivatedChanged: (Boolean) -> Unit,
    onMonthlyReportNotificationActivatedChanged: (Boolean) -> Unit,
    onRateAppClicked: () -> Unit,
    onShareAppClicked: () -> Unit,
    onUpdateNotificationActivatedChanged: (Boolean) -> Unit,
    onBugReportClicked: () -> Unit,
    onAppClicked: () -> Unit,
    onSubscribeButtonClicked: () -> Unit,
    onRedeemCodeButtonClicked: () -> Unit,
    onPushPermissionResult: () -> Unit,
    onAdjustLowMoneyWarningAmountChanged: (Int) -> Unit,
    navigateToPremium: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onNotificationPermissionDeniedPromptAccepted: () -> Unit,
) {
    val context = LocalContext.current
    val pushPermissionState = rememberPermissionStateCompat {
        onPushPermissionResult()
    }

    var showCurrencyPickerDialog by remember { mutableStateOf(false) }
    var showThemePickerDialogWithTheme by remember { mutableStateOf<AppTheme?>(null) }

    LaunchedEffect(key1 = "eventsListener") {
        launchCollect(eventFlow) { event ->
            when(event) {
                SettingsViewModel.Event.OpenBackupSettings -> navigateToBackupSettings()
                SettingsViewModel.Event.ShowCurrencyPicker -> showCurrencyPickerDialog = true
                is SettingsViewModel.Event.ShowLowMoneyWarningAmountPicker -> {
                    context.showLowMoneyWarningAmountPickerDialog(
                        lowMoneyWarningAmount = event.currentLowMoneyWarningAmount,
                        onLowMoneyWarningAmountChanged = onAdjustLowMoneyWarningAmountChanged,
                    )
                }
                SettingsViewModel.Event.AskForNotificationPermission -> {
                    if (pushPermissionState.status.isGranted) {
                        onPushPermissionResult()
                    } else {
                        pushPermissionState.launchPermissionRequest()
                    }
                }
                is SettingsViewModel.Event.OpenBugReport -> {
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SENDTO
                    sendIntent.data = Uri.parse("mailto:") // only email apps should handle this
                    sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.bug_report_email)))
                    sendIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.setting_category_bug_report_send_text, event.localId))
                    sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.setting_category_bug_report_send_subject))

                    if (context.packageManager != null && sendIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(sendIntent)
                    } else {
                        Toast.makeText(context, context.getString(R.string.setting_category_bug_report_send_error), Toast.LENGTH_SHORT).show()
                    }
                }
                SettingsViewModel.Event.OpenRedeemCode -> context.openRedeemCodeDialog()
                SettingsViewModel.Event.OpenSubscribeScreen -> navigateToPremium()
                SettingsViewModel.Event.RedirectToTwitter -> {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse("https://x.com/BenoitLetondor")
                    context.startActivity(i)
                }
                is SettingsViewModel.Event.ShowAppRating -> RatingPopup(context as Activity, event.parameters).show(true)
                SettingsViewModel.Event.ShowAppSharing -> {
                    try {
                        val sendIntent = Intent()
                        sendIntent.action = Intent.ACTION_SEND
                        sendIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_invite_message) + "\n" + "https://play.google.com/store/apps/details?id=com.benoitletondor.FinanceTrackerapp")
                        sendIntent.type = "text/plain"
                        context.startActivity(sendIntent)
                    } catch (e: Exception) {
                        Logger.error("An error occurred during sharing app activity start", e)
                    }
                }
                is SettingsViewModel.Event.ShowThemePicker -> showThemePickerDialogWithTheme = event.currentTheme
                SettingsViewModel.Event.ShowNotificationRejectedPrompt -> MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.setting_notification_permission_rejected_dialog_title)
                    .setMessage(R.string.setting_notification_permission_rejected_dialog_description)
                    .setPositiveButton(R.string.setting_notification_permission_rejected_dialog_accept_cta) { dialog, _ ->
                        dialog.dismiss()
                        onNotificationPermissionDeniedPromptAccepted()
                    }
                    .setNegativeButton(R.string.setting_notification_permission_rejected_dialog_not_now_cta) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    AppWithTopAppBarScaffold(
        title = stringResource(R.string.title_activity_settings),
        backButtonBehavior = BackButtonBehavior.NavigateBack(
            onBackButtonPressed = navigateUp,
        ),
        content = { contentPadding ->
            Box {
                val state by stateFlow.collectAsState()

                when(val currentState = state) {
                    is SettingsViewModel.State.Error -> ErrorView(
                        contentPadding = contentPadding,
                        error = currentState.error,
                        onRetryButtonClicked = onRetryButtonClicked,
                    )
                    is SettingsViewModel.State.Loaded -> Settings(
                        contentPadding = contentPadding,
                        state = currentState,
                        onCurrencyChangeClicked = onCurrencyChangeClicked,
                        onAdjustLowMoneyWarningAmountClicked = onAdjustLowMoneyWarningAmountClicked,
                        onFirstDayOfWeekChanged = onFirstDayOfWeekChanged,
                        onPremiumButtonClicked = onPremiumButtonClicked,
                        onProButtonClicked = onProButtonClicked,
                        onThemeClicked = onThemeClicked,
                        onShowCheckedBalanceChanged = onShowCheckedBalanceChanged,
                        onCloudBackupClicked = onCloudBackupClicked,
                        onDailyReminderNotificationActivatedChanged = onDailyReminderNotificationActivatedChanged,
                        onMonthlyReportNotificationActivatedChanged = onMonthlyReportNotificationActivatedChanged,
                        onRateAppClicked = onRateAppClicked,
                        onShareAppClicked = onShareAppClicked,
                        onUpdateNotificationActivatedChanged= onUpdateNotificationActivatedChanged,
                        onBugReportClicked = onBugReportClicked,
                        onAppClicked = onAppClicked,
                        onSubscribeButtonClicked = onSubscribeButtonClicked,
                        onRedeemCodeButtonClicked = onRedeemCodeButtonClicked,
                    )
                    SettingsViewModel.State.Loading -> LoadingView(
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                if (showCurrencyPickerDialog) {
                    SelectCurrencyDialog(
                        contentPadding = contentPadding,
                        onDismissRequest = { showCurrencyPickerDialog = false },
                    )
                }

                val currentThemeForThemePicker = showThemePickerDialogWithTheme
                if (currentThemeForThemePicker != null) {
                    ThemePickerDialog(
                        contentPadding = contentPadding,
                        currentTheme = currentThemeForThemePicker,
                        onThemeSelected = {
                            showThemePickerDialogWithTheme = null
                            onThemeSelected(it)
                        },
                        onDismissRequest = {
                            showThemePickerDialogWithTheme = null
                        },
                    )
                }
            }
        }
    )
}

