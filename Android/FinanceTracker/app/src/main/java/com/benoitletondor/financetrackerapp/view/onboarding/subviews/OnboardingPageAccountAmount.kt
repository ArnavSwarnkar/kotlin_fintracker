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
package com.benoitletondor.FinanceTrackerapp.view.onboarding.subviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benoitletondor.FinanceTrackerapp.R
import com.benoitletondor.FinanceTrackerapp.helper.CurrencyHelper
import com.benoitletondor.FinanceTrackerapp.helper.sanitizeFromUnsupportedInputForDecimals
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency

@Composable
fun OnboardingPageAccountAmount(
    contentPadding: PaddingValues,
    shouldFocusOnAccountAmountField: Boolean,
    userCurrencyFlow: StateFlow<Currency>,
    userMoneyAmountFlow: StateFlow<Double>,
    onNextPressed: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val currency by userCurrencyFlow.collectAsState()
    val currentAmount by userMoneyAmountFlow.collectAsState()

    var currentTextFieldValue by remember { mutableStateOf(
        TextFieldValue(
        text = "",
        selection = TextRange(index = 0),
        )
    ) }

    LaunchedEffect("initAmount") {
        val amountFromDB = userMoneyAmountFlow.value
        val formattedAmount = formatAmountValue(amountFromDB)
        if (amountFromDB != 0.0 && formattedAmount != currentTextFieldValue.text) {
            currentTextFieldValue = TextFieldValue(
                text = formattedAmount,
                selection = TextRange(index = formattedAmount.length),
            )
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(shouldFocusOnAccountAmountField) {
        if (shouldFocusOnAccountAmountField) {
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(R.color.secondary))
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.onboarding_screen_3_title),
                color = Color.White,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
            )

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.onboarding_screen_3_message),
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ){
                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    value = currentTextFieldValue,
                    onValueChange = { newValue ->
                        val newText = newValue.text.sanitizeFromUnsupportedInputForDecimals()

                        currentTextFieldValue = TextFieldValue(
                            text = newText,
                            selection = newValue.selection,
                        )
                        onAmountChange(newText)
                    },
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        autoCorrectEnabled = false,
                    ),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = currency.symbol,
                    color = Color.White,
                    fontSize = 30.sp,
                )
            }

        }

        Button(
            onClick = onNextPressed,
        ) {
            Text(
                text = stringResource(R.string.onboarding_screen_3_cta, CurrencyHelper.getFormattedCurrencyString(currency, currentAmount)),
                fontSize = 20.sp,
            )
        }
    }
}

private fun formatAmountValue(amount: Double): String = if (amount == 0.0) "0" else amount.toString()
