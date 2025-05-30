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
package com.benoitletondor.FinanceTrackerapp.view.main.subviews

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.benoitletondor.FinanceTrackerapp.R
import com.benoitletondor.FinanceTrackerapp.compose.AppTopAppBar
import com.benoitletondor.FinanceTrackerapp.compose.AppTopBarMoreMenuItem
import com.benoitletondor.FinanceTrackerapp.compose.BackButtonBehavior
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainViewTopBar(
    showActionButtonsFlow: StateFlow<Boolean>,
    showPremiumRelatedButtonsFlow: StateFlow<Boolean>,
    showManageAccountButtonFlow: StateFlow<Boolean>,
    showGoBackToCurrentMonthButtonFlow: StateFlow<Boolean>,
    onSettingsButtonPressed: () -> Unit,
    onAdjustCurrentBalanceButtonPressed: () -> Unit,
    onTickAllPastEntriesButtonPressed: () -> Unit,
    onManageAccountButtonPressed: () -> Unit,
    onDiscoverPremiumButtonPressed: () -> Unit,
    onMonthlyReportButtonPressed: () -> Unit,
    onGoBackToCurrentMonthButtonPressed: () -> Unit,
) {
    AppTopAppBar(
        title = stringResource(R.string.app_name),
        backButtonBehavior = BackButtonBehavior.Hidden,
        actions = {
            val showActionButtons by showActionButtonsFlow.collectAsState()
            val showPremiumRelatedButtons by showPremiumRelatedButtonsFlow.collectAsState()
            val showManageAccountButton by showManageAccountButtonFlow.collectAsState()
            val showGoBackToCurrentMonthButton by showGoBackToCurrentMonthButtonFlow.collectAsState()

            if (showActionButtons) {
                if (showManageAccountButton) {
                    IconButton(
                        onClick = onManageAccountButtonPressed,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_manage_accounts_24),
                            contentDescription = stringResource(R.string.action_manage_account),
                        )
                    }
                }

                if (showGoBackToCurrentMonthButton) {
                    IconButton(
                        onClick = onGoBackToCurrentMonthButtonPressed,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_today),
                            contentDescription = stringResource(R.string.action_go_to_current_month),
                        )
                    }
                }

                if (showPremiumRelatedButtons) {
                    IconButton(
                        onClick = onMonthlyReportButtonPressed,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_list_alt_24),
                            contentDescription = stringResource(R.string.monthly_report_button_title),
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDiscoverPremiumButtonPressed,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_star_24),
                            contentDescription = stringResource(R.string.action_become_premium),
                        )
                    }
                }
            }

            AppTopBarMoreMenuItem { dismiss ->
                if (showActionButtons) {
                    DropdownMenuItem(
                        onClick = {
                            onAdjustCurrentBalanceButtonPressed()
                            dismiss()
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.action_balance),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        },
                    )
                }

                if (showActionButtons && showPremiumRelatedButtons) {
                    DropdownMenuItem(
                        onClick = {
                            onTickAllPastEntriesButtonPressed()
                            dismiss()
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.action_mark_all_past_entries_as_checked),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                            )
                        },
                    )
                }

                DropdownMenuItem(
                    onClick = {
                        onSettingsButtonPressed()
                        dismiss()
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.action_settings),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                )
            }
        }
    )
}
