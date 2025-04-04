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

package com.benoitletondor.FinanceTrackerapp.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import java.time.LocalDate

@Immutable
@Parcelize
data class RecurringExpense(val id: Long?,
                            val title: String,
                            val amount: Double,
                            val recurringDate: LocalDate,
                            val modified: Boolean,
                            val type: RecurringExpenseType) : Parcelable {

    constructor(title: String,
                originalAmount: Double,
                recurringDate: LocalDate,
                type: RecurringExpenseType) : this(null, title, originalAmount, recurringDate, false, type)
}
