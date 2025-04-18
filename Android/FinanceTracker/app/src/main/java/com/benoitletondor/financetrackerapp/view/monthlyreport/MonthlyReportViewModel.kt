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
package com.benoitletondor.FinanceTrackerapp.view.monthlyreport

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benoitletondor.FinanceTrackerapp.helper.Logger
import com.benoitletondor.FinanceTrackerapp.helper.MutableLiveFlow
import com.benoitletondor.FinanceTrackerapp.helper.getListOfMonthsAvailableForUser
import com.benoitletondor.FinanceTrackerapp.helper.watchUserCurrency
import com.benoitletondor.FinanceTrackerapp.iab.Iab
import com.benoitletondor.FinanceTrackerapp.iab.PremiumCheckStatus
import com.benoitletondor.FinanceTrackerapp.injection.CurrentDBProvider
import com.benoitletondor.FinanceTrackerapp.injection.requireDB
import com.benoitletondor.FinanceTrackerapp.model.Expense
import com.benoitletondor.FinanceTrackerapp.parameters.Parameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

@HiltViewModel(assistedFactory = MonthlyReportViewModelFactory::class)
class MonthlyReportViewModel @AssistedInject constructor(
    iab: Iab,
    private val parameters: Parameters,
    private val currentDBProvider: CurrentDBProvider,
    @Assisted fromNotification: Boolean,
) : ViewModel() {

    val shouldShowExportToCsvButtonFlow: StateFlow<Boolean> = iab.iabStatusFlow
        .map { it == PremiumCheckStatus.PRO_SUBSCRIBED }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val userMonthPositionShiftMutableFlow = MutableStateFlow(if (fromNotification) -1 else 0)

    val userCurrencyStateFlow get() = parameters.watchUserCurrency()

    val stateFlow: StateFlow<State> = flow {
            val months = withContext(Dispatchers.IO) {
                parameters.getListOfMonthsAvailableForUser()
            }
            emit(months)
        }
        .flatMapLatest { months ->
            var currentMonthPosition = months.indexOf(YearMonth.now())
            if (currentMonthPosition == -1) {
                Logger.error("Error while getting current month position, returned -1", IllegalStateException("Current month not found in list of available months"))
                currentMonthPosition = months.size - 1
            }

            return@flatMapLatest userMonthPositionShiftMutableFlow
                .map { userMonthPositionShift ->
                    val selectedPosition = MonthlyReportSelectedPosition(
                        position = currentMonthPosition + userMonthPositionShift,
                        month = months[currentMonthPosition + userMonthPositionShift],
                        first = currentMonthPosition + userMonthPositionShift == 0,
                        last = currentMonthPosition + userMonthPositionShift >= months.size - 1,
                    )

                    return@map State.Loaded(months, selectedPosition)
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    private val retryLoadingMonthDataMutableFlow = MutableSharedFlow<Unit>()

    val monthDataStateFlow: StateFlow<MonthDataState> = stateFlow
        .filterIsInstance<State.Loaded>()
        .map { state ->
            val expensesForMonth = withContext(Dispatchers.Default) {
                currentDBProvider.requireDB.getExpensesForMonth(state.selectedPosition.month)
            }

            if( expensesForMonth.isEmpty() ) {
                return@map MonthDataState.Empty
            }

            val expenses = mutableListOf<Expense>()
            val revenues = mutableListOf<Expense>()
            var revenuesAmount = 0.0
            var expensesAmount = 0.0

            withContext(Dispatchers.Default) {
                for(expense in expensesForMonth) {
                    if( expense.isRevenue() ) {
                        revenues.add(expense)
                        revenuesAmount -= expense.amount
                    } else {
                        expenses.add(expense)
                        expensesAmount += expense.amount
                    }
                }
            }

            return@map MonthDataState.Loaded(expenses, revenues, expensesAmount, revenuesAmount)
        }
        .retryWhen { cause, _ ->
            Logger.error("Error while loading month data", cause)
            emit(MonthDataState.Error(cause))

            retryLoadingMonthDataMutableFlow.first()

            emit(MonthDataState.Loading)
            true
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MonthDataState.Loading)

    private val eventMutableFlow = MutableLiveFlow<Event>()
    val eventFlow: Flow<Event> = eventMutableFlow

    fun onExportToCsvButtonPressed() {
        viewModelScope.launch {
            eventMutableFlow.emit(Event.OpenExportToCsvScreen((stateFlow.value as State.Loaded).selectedPosition.month))
        }
    }

    fun onRetryLoadingMonthDataPressed() {
        viewModelScope.launch {
            retryLoadingMonthDataMutableFlow.emit(Unit)
        }
    }

    fun onPreviousMonthClicked() {
        val currentState = stateFlow.value
        if (currentState is State.Loaded && currentState.selectedPosition.first) {
            return
        }

        userMonthPositionShiftMutableFlow.value--
    }

    fun onNextMonthClicked() {
        val currentState = stateFlow.value
        if (currentState is State.Loaded && currentState.selectedPosition.last) {
            return
        }

        userMonthPositionShiftMutableFlow.value++
    }

    sealed class Event {
        data class OpenExportToCsvScreen(val month: YearMonth) : Event()
    }

    sealed class State {
        data object Loading : State()
        @Immutable
        data class Loaded(val months: List<YearMonth>, val selectedPosition: MonthlyReportSelectedPosition) : State()
    }

    sealed class MonthDataState {
        data object Loading : MonthDataState()
        data object Empty: MonthDataState()
        @Immutable
        data class Error(val error: Throwable) : MonthDataState()
        @Immutable
        data class Loaded(val expenses: List<Expense>, val revenues: List<Expense>, val expensesAmount: Double, val revenuesAmount: Double) : MonthDataState()
    }
}

@AssistedFactory
interface MonthlyReportViewModelFactory {
    fun create(fromNotification: Boolean): MonthlyReportViewModel
}

data class MonthlyReportSelectedPosition(
    val position: Int,
    val month: YearMonth,
    val first: Boolean,
    val last: Boolean,
)
