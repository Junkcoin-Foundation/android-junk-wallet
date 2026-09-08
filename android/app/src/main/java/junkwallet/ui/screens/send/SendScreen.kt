package junkwallet.ui.screens.send

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import junkwallet.ui.theme.*
import junkwallet.ui.viewmodel.SendViewModel
import junkwallet.domain.model.AddressType
import junkwallet.domain.wallet.AddressValidator

@Composable
fun SendScreen(
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
    onQrScan: () -> Unit = {},
    viewModel: SendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val validation by viewModel.validation.collectAsState()
    val addressInfo by viewModel.addressInfo.collectAsState()

    LaunchedEffect(uiState.isSent) {
        if (uiState.isSent && uiState.txId != null) {
            onSuccess(uiState.txId!!)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = when (uiState.currentStep) {
                        SendViewModel.Step.INPUT -> "Send JKC"
                        SendViewModel.Step.CONFIRM -> "Confirm Transaction"
                        SendViewModel.Step.BROADCAST -> "Broadcasting..."
                        SendViewModel.Step.SUCCESS -> "Sent!"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StepIndicator(
                    step = "1",
                    label = "INPUT",
                    isActive = uiState.currentStep == SendViewModel.Step.INPUT,
                    isCompleted = uiState.currentStep.ordinal > SendViewModel.Step.INPUT.ordinal
                )
                StepIndicator(
                    step = "2",
                    label = "CONFIRM",
                    isActive = uiState.currentStep == SendViewModel.Step.CONFIRM,
                    isCompleted = uiState.currentStep.ordinal > SendViewModel.Step.CONFIRM.ordinal
                )
                StepIndicator(
                    step = "3",
                    label = "BROADCAST",
                    isActive = uiState.currentStep == SendViewModel.Step.BROADCAST,
                    isCompleted = uiState.currentStep == SendViewModel.Step.SUCCESS
                )
                StepIndicator(
                    step = "4",
                    label = "SUCCESS",
                    isActive = uiState.currentStep == SendViewModel.Step.SUCCESS,
                    isCompleted = false
                )
            }

            // Content based on step
            when (uiState.currentStep) {
                SendViewModel.Step.INPUT -> {
                    InputStep(
                        recipientAddress = uiState.recipientAddress,
                        onAddressChange = { viewModel.updateRecipientAddress(it) },
                        amount = uiState.amount,
                        onAmountChange = { viewModel.updateAmount(it) },
                        opReturnData = uiState.opReturnData,
                        onOpReturnChange = { viewModel.updateOpReturnData(it) },
                        addressValidation = validation,
                        addressTypeInfo = addressInfo,
                        isAddressValid = uiState.isAddressValid,
                        balance = uiState.balance,
                        feeEstimate = uiState.feeEstimate,
                        feeLevel = uiState.feeLevel,
                        onFeeLevelChange = { viewModel.setFeeLevel(it) },
                        customFeeRate = uiState.customFeeRate,
                        onCustomFeeRateChange = { viewModel.updateCustomFeeRate(it) },
                        onMaxClick = { viewModel.fillMaxAmount() },
                        onSend = { viewModel.startConfirmation() },
                        onQrScan = onQrScan
                    )
                }

                SendViewModel.Step.CONFIRM -> {
                    ConfirmStep(
                        recipientAddress = uiState.recipientAddress,
                        amountSatoshis = uiState.sentAmountSatoshis,
                        feeSatoshis = uiState.feeEstimate,
                        totalSatoshis = uiState.sentAmountSatoshis + uiState.feeEstimate,
                        onConfirm = { viewModel.broadcast() },
                        onBack = { viewModel.previousStep() },
                        addressType = addressInfo?.type
                    )
                }

                SendViewModel.Step.BROADCAST -> {
                    BroadcastStep()
                }

                SendViewModel.Step.SUCCESS -> {
                    SuccessStep(
                        txId = uiState.txId ?: "",
                        feeSatoshis = uiState.feeEstimate,
                        amountSatoshis = uiState.sentAmountSatoshis,
                        recipientAddress = uiState.recipientAddress
                    )
                }
            }

            // Error display
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepIndicator(
    step: String,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = step,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InputStep(
    recipientAddress: String,
    onAddressChange: (String) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    opReturnData: String,
    onOpReturnChange: (String) -> Unit,
    addressValidation: AddressValidator.ValidationResult?,
    addressTypeInfo: AddressValidator.AddressTypeInfo?,
    isAddressValid: Boolean,
    balance: Long,
    feeEstimate: Long,
    feeLevel: SendViewModel.FeeLevel,
    onFeeLevelChange: (SendViewModel.FeeLevel) -> Unit,
    customFeeRate: String,
    onCustomFeeRateChange: (String) -> Unit,
    onMaxClick: () -> Unit,
    onSend: () -> Unit,
    onQrScan: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Recipient Address
        Text(
            text = "Recipient Address",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = recipientAddress,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Enter Junkcoin address",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (recipientAddress.isNotEmpty()) {
                    IconButton(onClick = { onAddressChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = onQrScan) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            isError = addressValidation?.isValid == false,
            supportingText = {
                if (addressValidation?.isValid == false) {
                    Text(
                        text = addressValidation.error ?: "Invalid address",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Address type info
        if (isAddressValid && addressTypeInfo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = addressTypeInfo.icon,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = addressTypeInfo.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = addressTypeInfo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    FeeLevelChip(feeLevel = addressTypeInfo.feeLevel)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Amount with MAX button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Amount (JKC)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onMaxClick) {
                Text(
                    text = "MAX",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "0.00",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Text(
                    text = "JKC",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Balance info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Available: ${String.format("%.8f", balance / 100_000_000.0)} JKC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Fee Level Selector
        Text(
            text = "Fee Level",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SendViewModel.FeeLevel.entries.filter { it != SendViewModel.FeeLevel.CUSTOM }.forEach { level ->
                FilterChip(
                    selected = feeLevel == level,
                    onClick = { onFeeLevelChange(level) },
                    label = {
                        Text(
                            text = level.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Custom fee slider
        if (feeLevel == SendViewModel.FeeLevel.CUSTOM) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customFeeRate,
                    onValueChange = onCustomFeeRateChange,
                    modifier = Modifier.width(100.dp),
                    label = { Text("sat/vB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = customFeeRate.toFloatOrNull()?.coerceIn(1f, 100f) ?: 10f,
                    onValueChange = { onCustomFeeRateChange(it.toInt().toString()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Fee estimate display
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Network Fee: ${String.format("%.8f", feeEstimate / 100_000_000.0)} JKC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${feeLevel.label} · ${feeLevel.satPerVByte} sat/vB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // OP_RETURN Data (optional)
        Text(
            text = "OP_RETURN Data (optional)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = opReturnData,
            onValueChange = onOpReturnChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Hex data to embed (max 80 bytes)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Send button
        Button(
            onClick = onSend,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isAddressValid && amount.toDoubleOrNull() != null && amount.toDouble() > 0,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Review Transaction",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FeeLevelChip(feeLevel: AddressValidator.FeeLevel) {
    val (text, color) = when (feeLevel) {
        AddressValidator.FeeLevel.HIGH -> "High Fees" to MaterialTheme.colorScheme.error
        AddressValidator.FeeLevel.MEDIUM -> "Medium Fees" to MaterialTheme.colorScheme.tertiary
        AddressValidator.FeeLevel.LOW -> "Low Fees" to MaterialTheme.colorScheme.primary
        AddressValidator.FeeLevel.LOWEST -> "Lowest Fees" to MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConfirmStep(
    recipientAddress: String,
    amountSatoshis: Long,
    feeSatoshis: Long,
    totalSatoshis: Long,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    addressType: AddressType?
) {
    val amountJkc = String.format("%.8f", amountSatoshis / 100_000_000.0)
    val feeJkc = String.format("%.8f", feeSatoshis / 100_000_000.0)
    val totalJkc = String.format("%.8f", totalSatoshis / 100_000_000.0)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Recipient
                Text(
                    text = "Recipient",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = recipientAddress,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Text(
                    text = "Amount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$amountJkc JKC",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Fee
                Text(
                    text = "Network Fee",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$feeJkc JKC",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Address type badge
                if (addressType != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val typeInfo = AddressValidator.getAddressTypeInfo(addressType)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = typeInfo.icon, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = typeInfo.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()

                // Total
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "$totalJkc JKC",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Confirm button
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Confirm & Send",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Back button
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Go Back",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun BroadcastStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Broadcasting transaction...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please wait while your transaction is sent to the network",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessStep(
    txId: String,
    feeSatoshis: Long,
    amountSatoshis: Long,
    recipientAddress: String
) {
    val amountJkc = String.format("%.8f", amountSatoshis / 100_000_000.0)
    val feeJkc = String.format("%.8f", feeSatoshis / 100_000_000.0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Transaction Sent!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your transaction has been broadcast to the network",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Transaction ID",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = txId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Amount Sent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$amountJkc JKC",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Network Fee",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$feeJkc JKC",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { /* TODO: Open explorer */ },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "View in Explorer",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun HorizontalDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun Surface(
    color: Color,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color)
    ) {
        content()
    }
}
