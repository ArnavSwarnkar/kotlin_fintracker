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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benoitletondor.FinanceTrackerapp.helper.AppTheme
import com.benoitletondor.FinanceTrackerapp.helper.Logger
import com.benoitletondor.FinanceTrackerapp.helper.MutableLiveFlow
import com.benoitletondor.FinanceTrackerapp.helper.combine
import com.benoitletondor.FinanceTrackerapp.helper.watchUserCurrency
import com.benoitletondor.FinanceTrackerapp.iab.Iab
import com.benoitletondor.FinanceTrackerapp.iab.PremiumCheckStatus
import com.benoitletondor.FinanceTrackerapp.parameters.Parameters
import com.benoitletondor.FinanceTrackerapp.parameters.setFirstDayOfWeek
import com.benoitletondor.FinanceTrackerapp.parameters.watchFirstDayOfWeek
import com.benoitletondor.FinanceTrackerapp.parameters.watchIsBackupEnabled
import com.benoitletondor.FinanceTrackerapp.parameters.watchLowMoneyWarningAmount
import com.benoitletondor.FinanceTrackerapp.parameters.watchShouldShowCheckedBalance
import com.benoitletondor.FinanceTrackerapp.parameters.watchTheme
import com.benoitletondor.FinanceTrackerapp.parameters.watchUserAllowingDailyReminderPushes
import com.benoitletondor.FinanceTrackerapp.parameters.watchUserAllowingMonthlyReminderPushes
import com.benoitletondor.FinanceTrackerapp.parameters.watchUserAllowingUpdatePushes
import com.benoitletondor.FinanceTrackerapp.BuildConfig
import com.benoitletondor.FinanceTrackerapp.parameters.getLocalId
import com.benoitletondor.FinanceTrackerapp.parameters.setLowMoneyWarningAmount
import com.benoitletondor.FinanceTrackerapp.parameters.setShouldShowCheckedBalance
import com.benoitletondor.FinanceTrackerapp.parameters.setTheme
import com.benoitletondor.FinanceTrackerapp.parameters.setUserAllowDailyReminderPushes
import com.benoitletondor.FinanceTrackerapp.parameters.setUserAllowMonthlyReminderPushes
import com.benoitletondor.FinanceTrackerapp.parameters.setUserAllowUpdatePushes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.Currency
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val parameters: Parameters,
    iab: Iab,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val retryLoadingMutableFlow = MutableSharedFlow<Unit>()

    private val isNotificationPermissionGrantedMutableFlow = MutableStateFlow(isNotificationPermissionGranted())

    val stateFlow: StateFlow<State> = combine(
        parameters.watchUserCurrency(),
        parameters.watchLowMoneyWarningAmount(),
        parameters.watchFirstDayOfWeek(),
        iab.iabStatusFlow
            .flatMapLatest { iabStatus ->
                 when(iabStatus) {
                     PremiumCheckStatus.INITIALIZING,
                     PremiumCheckStatus.CHECKING -> flowOf(SubscriptionStatus.Loading)
                     PremiumCheckStatus.ERROR -> flowOf(SubscriptionStatus.Error)
                     PremiumCheckStatus.NOT_PREMIUM -> flowOf(SubscriptionStatus.NotSubscribed)
                     PremiumCheckStatus.LEGACY_PREMIUM,
                     PremiumCheckStatus.PREMIUM_SUBSCRIBED,
                     PremiumCheckStatus.PRO_SUBSCRIBED -> {
                         combine(
                             parameters.watchIsBackupEnabled(),
                             parameters.watchTheme(),
                             parameters.watchShouldShowCheckedBalance(),
                             isNotificationPermissionGrantedMutableFlow,
                             parameters.watchUserAllowingDailyReminderPushes(),
                             parameters.watchUserAllowingMonthlyReminderPushes(),
                         ) { isBackupEnabled, theme, showCheckedBalance, isNotificationPermissionGranted, dailyReminderActivated, monthlyReportNotificationActivated ->
                             when(iabStatus) {
                                 PremiumCheckStatus.LEGACY_PREMIUM,
                                 PremiumCheckStatus.PREMIUM_SUBSCRIBED -> SubscriptionStatus.PremiumSubscribed(
                                     isBackupEnabled,
                                     theme,
                                     showCheckedBalance,
                                     dailyReminderActivated = isNotificationPermissionGranted && dailyReminderActivated,
                                     monthlyReportNotificationActivated = isNotificationPermissionGranted && monthlyReportNotificationActivated,
                                 )
                                 PremiumCheckStatus.PRO_SUBSCRIBED -> SubscriptionStatus.ProSubscribed(
                                     isBackupEnabled,
                                     theme,
                                     showCheckedBalance,
                                     dailyReminderActivated = isNotificationPermissionGranted && dailyReminderActivated,
                                     monthlyReportNotificationActivated = isNotificationPermissionGranted && monthlyReportNotificationActivated,
                                 )
                                 else -> throw IllegalStateException("Unable to handle status $iabStatus")
                             }

                         }
                     }
                 }
            },
        isNotificationPermissionGrantedMutableFlow,
        parameters.watchUserAllowingUpdatePushes(),
    ) { userCurrency, lowMoneyWarningAmount, firstDayOfWeek, subscriptionStatus, isNotificationPermissionGranted, userAllowingUpdatePushes ->
        return@combine State.Loaded(
            userCurrency,
            lowMoneyWarningAmount,
            firstDayOfWeek,
            subscriptionStatus,
            userAllowingUpdatePushes = isNotificationPermissionGranted && userAllowingUpdatePushes,
            appVersion = BuildConfig.VERSION_NAME,
        ) as State
    }
        .retryWhen { cause, _ ->
            Logger.error("Error loading settings", cause)
            emit(State.Error(cause))

            retryLoadingMutableFlow.first()
            emit(State.Loading)

            true
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    private val eventMutableFlow = MutableLiveFlow<Event>()
    val eventFlow: Flow<Event> = eventMutableFlow

    fun onRetryButtonPressed() {
        viewModelScope.launch {
            retryLoadingMutableFlow.emit(Unit)
        }
    }

    fun onCurrencyChangeClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.ShowCurrencyPicker)
        }
    }

    fun onAdjustLowMoneyWarningAmountClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.ShowLowMoneyWarningAmountPicker((stateFlow.value as State.Loaded).lowMoneyWarningAmount))
        }
    }

    fun onFirstDayOfWeekChanged(dayOfWeek: DayOfWeek) {
        viewModelScope.launch {
            parameters.setFirstDayOfWeek(dayOfWeek)
        }
    }

    fun onPremiumButtonClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenSubscribeScreen)
        }
    }

    fun onProButtonClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenSubscribeScreen)
        }
    }

    fun onThemeClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.ShowThemePicker(
                currentTheme = ((stateFlow.value as State.Loaded).subscriptionStatus as SubscriptionStatus.Subscribed).theme),
            )
        }
    }

    fun onShowCheckedBalanceChanged(showCheckedBalance: Boolean) {
        viewModelScope.launch {
            parameters.setShouldShowCheckedBalance(showCheckedBalance)
        }
    }

    fun onCloudBackupClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenBackupSettings)
        }
    }

    fun onDailyReminderNotificationActivatedChanged(activated: Boolean) {
        viewModelScope.launch {
            parameters.setUserAllowDailyReminderPushes(activated)
            if (activated) {
                eventMutableFlow.emit(Event.AskForNotificationPermission)
            }
        }
    }

    fun onMonthlyReportNotificationActivatedChanged(activated: Boolean) {
        viewModelScope.launch {
            parameters.setUserAllowMonthlyReminderPushes(activated)
            if (activated) {
                eventMutableFlow.emit(Event.AskForNotificationPermission)
            }
        }
    }

    fun onPushPermissionResult() {
        val granted = isNotificationPermissionGranted()

        isNotificationPermissionGrantedMutableFlow.value = granted

        if (!granted) {
            viewModelScope.launch {
                eventMutableFlow.emit(Event.ShowNotificationRejectedPrompt)
            }
        }
    }

    fun onRateAppClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.ShowAppRating(parameters = parameters))
        }
    }

    fun onShareAppClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.ShowAppSharing)
        }
    }

    fun onUpdateNotificationActivatedChanged(activated: Boolean) {
        viewModelScope.launch {
            parameters.setUserAllowUpdatePushes(activated)
            if (activated) {
                eventMutableFlow.emit(Event.AskForNotificationPermission)
            }
        }
    }

    fun onBugReportClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenBugReport(
                localId = parameters.getLocalId() ?: "UNKNOWN_LOCAL_ID"
            ))
        }
    }

    fun onAppClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.RedirectToTwitter)
        }
    }

    fun onSubscribeButtonClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenSubscribeScreen)
        }
    }

    fun onRedeemCodeButtonClicked() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenRedeemCode)
        }
    }

    fun onAdjustLowMoneyWarningAmountChanged(newLowMoneyWarningAmount: Int) {
        viewModelScope.launch {
            parameters.setLowMoneyWarningAmount(newLowMoneyWarningAmount)
        }
    }

    fun onThemeSelected(theme: AppTheme) {
        viewModelScope.launch {
            parameters.setTheme(theme)
            AppCompatDelegate.setDefaultNightMode(theme.toPlatformValue())
        }
    }

    fun onNotificationPermissionDeniedPromptAccepted() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.AskForNotificationPermission)
        }
    }

    private fun isNotificationPermissionGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    sealed class State {
        data object Loading : State()
        data class Error(val error: Throwable) : State()
        data class Loaded(
            val userCurrency: Currency,
            val lowMoneyWarningAmount: Int,
            val firstDayOfWeek: DayOfWeek,
            val subscriptionStatus: SubscriptionStatus,
            val userAllowingUpdatePushes: Boolean,
            val appVersion: String,
        ) : State()
    }

    sealed class SubscriptionStatus {
        sealed interface Subscribed {
            val cloudBackupEnabled: Boolean
            val theme: AppTheme
            val showCheckedBalance: Boolean
            val dailyReminderActivated: Boolean
            val monthlyReportNotificationActivated: Boolean
        }

        data object Loading : SubscriptionStatus()
        data object Error : SubscriptionStatus()
        data object NotSubscribed : SubscriptionStatus()
        data class ProSubscribed(
            override val cloudBackupEnabled: Boolean,
            override val theme: AppTheme,
            override val showCheckedBalance: Boolean,
            override val dailyReminderActivated: Boolean,
            override val monthlyReportNotificationActivated: Boolean
        ) : SubscriptionStatus(), Subscribed
        data class PremiumSubscribed(
            override val cloudBackupEnabled: Boolean,
            override val theme: AppTheme,
            override val showCheckedBalance: Boolean,
            override val dailyReminderActivated: Boolean,
            override val monthlyReportNotificationActivated: Boolean
        ) : SubscriptionStatus(), Subscribed
    }

    sealed class Event {
        data object OpenBackupSettings : Event()
        data object ShowCurrencyPicker : Event()
        data class ShowLowMoneyWarningAmountPicker(val currentLowMoneyWarningAmount: Int) : Event()
        data object OpenSubscribeScreen : Event()
        data class ShowThemePicker(val currentTheme: AppTheme) : Event()
        data object AskForNotificationPermission : Event()
        data class ShowAppRating(val parameters: Parameters) : Event()
        data object ShowAppSharing : Event()
        data class OpenBugReport(val localId: String) : Event()
        data object RedirectToTwitter : Event()
        data object OpenRedeemCode : Event()
        data object ShowNotificationRejectedPrompt : Event()
    }
}
