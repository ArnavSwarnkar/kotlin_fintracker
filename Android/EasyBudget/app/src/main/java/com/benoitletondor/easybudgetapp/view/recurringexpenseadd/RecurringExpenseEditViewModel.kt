/*
 *   Copyright 2022 Benoit LETONDOR
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

package com.benoitletondor.easybudgetapp.view.recurringexpenseadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benoitletondor.easybudgetapp.helper.Logger
import com.benoitletondor.easybudgetapp.model.Expense
import com.benoitletondor.easybudgetapp.model.RecurringExpenseType
import com.benoitletondor.easybudgetapp.db.DB
import com.benoitletondor.easybudgetapp.helper.MutableLiveFlow
import kotlinx.coroutines.launch
import com.benoitletondor.easybudgetapp.model.RecurringExpense
import com.benoitletondor.easybudgetapp.parameters.Parameters
import com.benoitletondor.easybudgetapp.parameters.getInitDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class RecurringExpenseEditViewModel @Inject constructor(
    private val db: DB,
    private val parameters: Parameters,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * Expense that is being edited (will be null if it's a new one)
     */
    private val editedExpense: Expense? = savedStateHandle.get<Expense>("expense")

    private val expenseDateMutableStateFlow = MutableStateFlow(LocalDate.ofEpochDay(savedStateHandle.get<Long>("dateStart") ?: 0))
    val expenseDateFlow: Flow<LocalDate> = expenseDateMutableStateFlow

    private val editTypeMutableStateFlow = MutableStateFlow(ExpenseEditType(
        editedExpense?.isRevenue() ?: false,
        editedExpense != null
    ))
    val editTypeFlow: Flow<ExpenseEditType> = editTypeMutableStateFlow

    val existingExpenseData = editedExpense?.let { expense ->
        ExistingExpenseData(expense.title, expense.amount, expense.associatedRecurringExpense!!.type)
    }

    private val savingStateMutableStateFlow: MutableStateFlow<SavingState> = MutableStateFlow(SavingState.Idle)
    val savingStateFlow: Flow<SavingState> = savingStateMutableStateFlow

    private val expenseAddBeforeInitDateErrorMutableFlow = MutableLiveFlow<Unit>()
    val expenseAddBeforeInitDateEventFlow: Flow<Unit> = expenseAddBeforeInitDateErrorMutableFlow

    private val finishMutableFlow = MutableLiveFlow<Unit>()
    val finishFlow: Flow<Unit> = finishMutableFlow

    private val errorMutableFlow = MutableLiveFlow<Unit>()
    val errorFlow: Flow<Unit> = errorMutableFlow

    fun onExpenseRevenueValueChanged(isRevenue: Boolean) {
        editTypeMutableStateFlow.value = ExpenseEditType(isRevenue, editedExpense != null)
    }

    fun onSave(value: Double, description: String, recurringExpenseType: RecurringExpenseType) {
        val isRevenue = editTypeMutableStateFlow.value.isRevenue
        val date = expenseDateMutableStateFlow.value

        val dateOfInstallation = parameters.getInitDate() ?: LocalDate.now()

        if( date.isBefore(dateOfInstallation) ) {
            viewModelScope.launch {
                expenseAddBeforeInitDateErrorMutableFlow.emit(Unit)
            }

            return
        }

        doSaveExpense(value, description, recurringExpenseType, editedExpense, isRevenue, date)
    }

    fun onAddExpenseBeforeInitDateConfirmed(value: Double, description: String, recurringExpenseType: RecurringExpenseType) {
        val isRevenue = editTypeMutableStateFlow.value.isRevenue
        val date = expenseDateMutableStateFlow.value

        doSaveExpense(value, description, recurringExpenseType, editedExpense, isRevenue, date)
    }

    fun onAddExpenseBeforeInitDateCancelled() {
        // No-op
    }

    private fun doSaveExpense(
        value: Double,
        description: String,
        recurringExpenseType: RecurringExpenseType,
        editedExpense: Expense?,
        isRevenue: Boolean,
        date: LocalDate,
    ) {
        savingStateMutableStateFlow.value = SavingState.Saving(isRevenue)

        viewModelScope.launch {
            try {
                val inserted = withContext(Dispatchers.Default) {
                    if( editedExpense == null ) {
                        val insertedExpense = try {
                            db.persistRecurringExpense(RecurringExpense(description, if (isRevenue) -value else value, date, recurringExpenseType))
                        } catch (t: Throwable) {
                            Logger.error(false, "Error while inserting recurring expense into DB: addRecurringExpense returned false")
                            return@withContext false
                        }

                        if( !flattenExpensesForRecurringExpense(insertedExpense, date) ) {
                            Logger.error(false, "Error while flattening expenses for recurring expense: flattenExpensesForRecurringExpense returned false")
                            return@withContext false
                        }

                        return@withContext true
                    } else {
                        val recurringExpense = try {
                            val recurringExpense = editedExpense.associatedRecurringExpense!!
                            db.deleteAllExpenseForRecurringExpenseAfterDate(recurringExpense, editedExpense.date)
                            db.deleteExpense(editedExpense)

                            val newRecurringExpense = recurringExpense.copy(
                                modified = true,
                                type = recurringExpenseType,
                                recurringDate = date,
                                title = description,
                                amount = if (isRevenue) -value else value
                            )
                            db.persistRecurringExpense(newRecurringExpense)
                        } catch (t: Throwable) {
                            Logger.error(false, "Error while editing recurring expense into DB: addRecurringExpense returned false")
                            return@withContext false
                        }

                        if( !flattenExpensesForRecurringExpense(recurringExpense, date) ) {
                            Logger.error(false, "Error while flattening expenses for recurring expense edit: flattenExpensesForRecurringExpense returned false")
                            return@withContext false
                        }

                        return@withContext true
                    }
                }

                if( inserted ) {
                    finishMutableFlow.emit(Unit)
                } else {
                    errorMutableFlow.emit(Unit)
                }
            } finally {
                savingStateMutableStateFlow.value = SavingState.Idle
            }
        }
    }

    private suspend fun flattenExpensesForRecurringExpense(expense: RecurringExpense, date: LocalDate): Boolean
    {
        var currentDate = date

        when (expense.type) {
            RecurringExpenseType.DAILY -> {
                // Add up to 5 years of expenses
                for (i in 0 until 365*5) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false")
                        return false
                    }

                    currentDate = currentDate.plusDays(1)
                }
            }
            RecurringExpenseType.WEEKLY -> {
                // Add up to 5 years of expenses
                for (i in 0 until 12*4*5) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plus(1, ChronoUnit.WEEKS)
                }
            }
            RecurringExpenseType.BI_WEEKLY -> {
                // Add up to 5 years of expenses
                for (i in 0 until 12*4*5) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plus(2, ChronoUnit.WEEKS)
                }
            }
            RecurringExpenseType.TER_WEEKLY -> {
                // Add up to 5 years of expenses
                for (i in 0 until 12*4*5) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plus(3, ChronoUnit.WEEKS)
                }
            }
            RecurringExpenseType.FOUR_WEEKLY -> {
                // Add up to 5 years of expenses
                for (i in 0 until 12*4*5) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate.plus(2, ChronoUnit.WEEKS), false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plus(4, ChronoUnit.WEEKS)
                }
            }
            RecurringExpenseType.MONTHLY -> {
                // Add up to 10 years of expenses
                for (i in 0 until 12*10) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plusMonths(1)
                }
            }
            RecurringExpenseType.BI_MONTHLY -> {
                // Add up to 25 years of expenses
                for (i in 0 until 6*25) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plusMonths(2)
                }
            }
            RecurringExpenseType.TER_MONTHLY -> {
                // Add up to 25 years of expenses
                for (i in 0 until 4*25) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plusMonths(3)
                }
            }
            RecurringExpenseType.SIX_MONTHLY -> {
                // Add up to 25 years of expenses
                for (i in 0 until 2*25) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false", t)
                        return false
                    }

                    currentDate = currentDate.plusMonths(6)
                }
            }
            RecurringExpenseType.YEARLY -> {
                // Add up to 100 years of expenses
                for (i in 0 until 100) {
                    try {
                        db.persistExpense(Expense(expense.title, expense.amount, currentDate, false, expense))
                    } catch (t: Throwable) {
                        Logger.error(false, "Error while inserting expense for recurring expense into DB: persistExpense returned false")
                        return false
                    }

                    currentDate = currentDate.plusYears(1)
                }
            }
        }

        return true
    }

    fun onDateChanged(date: LocalDate) {
        expenseDateMutableStateFlow.value = date
    }

    sealed class SavingState {
        object Idle : SavingState()
        data class Saving(val isRevenue: Boolean): SavingState()
    }
}

data class ExpenseEditType(val isRevenue: Boolean, val editing: Boolean)

data class ExistingExpenseData(val title: String, val amount: Double, val type: RecurringExpenseType)