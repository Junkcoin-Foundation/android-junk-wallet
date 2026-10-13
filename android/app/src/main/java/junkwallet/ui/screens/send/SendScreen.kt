package junkwallet.ui.screens.send

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import junkwallet.domain.model.AddressType
import junkwallet.domain.wallet.AddressValidator
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TertiaryAmber
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMonospace
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.viewmodel.SendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
    onQrScan: () -> Unit = {},
    sourceAddressType: AddressType = AddressType.P2PKH,
    mwebBalance: Long = 0L,
    viewModel: SendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val validation by viewModel.validation.collectAsState()
    val addressInfo by viewModel.addressInfo.collectAsState()

    // Funding source follows the home address-type switcher.
    LaunchedEffect(sourceAddressType, mwebBalance) {
        viewModel.setSource(sourceAddressType, mwebBalance)
    }

    LaunchedEffect(uiState.isSent) {
        if (uiState.isSent && uiState.txId != null) {
            onSuccess(uiState.txId!!)
        }
    }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (uiState.currentStep) {
                                SendViewModel.Step.INPUT -> "Send JKC"
                                SendViewModel.Step.CONFIRM -> "Confirm Send"
                                SendViewModel.Step.BROADCAST -> "Broadcasting"
                                SendViewModel.Step.SUCCESS -> "Sent Successfully"
                            },
                            fontWeight = FontWeight.Bold,
                            color = TextHighEmphasis,
                            fontSize = 18.sp
                        )
                        if (uiState.currentStep == SendViewModel.Step.INPUT && uiState.sourceType == AddressType.MWEB) {
                            Text(
                                text = "From MWEB Confidential Balance",
                                fontSize = 11.sp,
                                color = TertiaryAmber
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep == SendViewModel.Step.CONFIRM) {
                            viewModel.previousStep()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextHighEmphasis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextHighEmphasis
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Content based on step
            when (uiState.currentStep) {
                SendViewModel.Step.INPUT -> {
                    if (uiState.qrBanner) {
                        QrScannedBanner(
                            address = uiState.recipientAddress,
                            amount = uiState.amount,
                            opReturnData = uiState.opReturnData,
                            onDismiss = { viewModel.dismissQrBanner() }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    CompactInputStep(
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
                        onQrScan = onQrScan,
                        isMwebSource = uiState.sourceType == AddressType.MWEB
                    )
                }

                SendViewModel.Step.CONFIRM -> {
                    CompactConfirmStep(
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
                    CompactBroadcastStep()
                }

                SendViewModel.Step.SUCCESS -> {
                    CompactSuccessStep(
                        txId = uiState.txId ?: "",
                        feeSatoshis = uiState.feeEstimate,
                        amountSatoshis = uiState.sentAmountSatoshis,
                        recipientAddress = uiState.recipientAddress,
                        onDone = {
                            if (uiState.txId != null) {
                                onSuccess(uiState.txId!!)
                            } else {
                                onBack()
                            }
                        }
                    )
                }
            }

            // Error display
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorCrimson.copy(alpha = 0.15f))
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
                            tint = ErrorCrimson,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.error ?: "",
                            color = ErrorCrimson,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = ErrorCrimson, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInputStep(
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
    onQrScan: () -> Unit,
    isMwebSource: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showMemo by remember { mutableStateOf(opReturnData.isNotEmpty()) }
    var showCustomFee by remember { mutableStateOf(feeLevel == SendViewModel.FeeLevel.CUSTOM) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // ── 1. Recipient Address Field with inline Paste & Scan ──
        Text(
            text = "Recipient",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = recipientAddress,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Enter or paste Junkcoin address", color = TextMuted, fontSize = 13.sp)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (recipientAddress.isNotEmpty()) {
                        IconButton(onClick = { onAddressChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextMuted)
                        }
                    } else {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                onAddressChange(clip.trim())
                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = PrimaryCyan)
                        }
                        IconButton(onClick = onQrScan) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR", tint = PrimaryCyan)
                        }
                    }
                }
            },
            isError = addressValidation?.isValid == false,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainer,
                unfocusedContainerColor = SurfaceContainer,
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = ErrorCrimson
            ),
            singleLine = true
        )

        // Address validation & format chip
        if (addressValidation?.isValid == false) {
            Text(
                text = addressValidation.error ?: "Invalid address",
                color = ErrorCrimson,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        } else if (isAddressValid && addressTypeInfo != null) {
            Row(
                modifier = Modifier
                    .padding(start = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (addressTypeInfo.type == AddressType.MWEB) TertiaryAmber.copy(alpha = 0.15f)
                            else PrimaryCyan.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${addressTypeInfo.icon} ${addressTypeInfo.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (addressTypeInfo.type == AddressType.MWEB) TertiaryAmber else PrimaryCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── 2. Amount Field with embedded MAX chip & Available Balance ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Amount",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
            val balanceJkc = String.format("%.8f", balance / 100_000_000.0).trimEnd('0').trimEnd('.')
            Text(
                text = "Avail: $balanceJkc JKC",
                fontSize = 11.sp,
                color = PrimaryCyan,
                modifier = Modifier.clickable { onMaxClick() }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("0.0", color = TextMuted, fontSize = 16.sp) },
            leadingIcon = {
                Text(
                    text = "JKC",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryCyan,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryCyan.copy(alpha = 0.15f))
                        .clickable { onMaxClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "MAX",
                        color = PrimaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainer,
                unfocusedContainerColor = SurfaceContainer,
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── 3. Compact Fee Level Selector ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Priority",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
            val feeJkc = String.format("%.8f", feeEstimate / 100_000_000.0).trimEnd('0').trimEnd('.')
            Text(
                text = "Fee: ~$feeJkc JKC",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                SendViewModel.FeeLevel.ECONOMY to "🐢 Slow",
                SendViewModel.FeeLevel.NORMAL to "⚡ Normal",
                SendViewModel.FeeLevel.PRIORITY to "🚀 Fast"
            )

            presets.forEach { (level, title) ->
                val isSelected = feeLevel == level && !showCustomFee
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        showCustomFee = false
                        onFeeLevelChange(level)
                    },
                    label = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan.copy(alpha = 0.18f),
                        selectedLabelColor = PrimaryCyan,
                        containerColor = SurfaceContainer,
                        labelColor = TextMuted
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            // Custom fee button
            IconButton(
                onClick = {
                    showCustomFee = !showCustomFee
                    if (showCustomFee) onFeeLevelChange(SendViewModel.FeeLevel.CUSTOM)
                    else onFeeLevelChange(SendViewModel.FeeLevel.NORMAL)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (showCustomFee) PrimaryCyan.copy(alpha = 0.18f) else SurfaceContainer)
                    .size(38.dp)
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Custom Fee",
                    tint = if (showCustomFee) PrimaryCyan else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Custom fee slider (collapsible)
        AnimatedVisibility(visible = showCustomFee) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$customFeeRate sat/vB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan,
                        modifier = Modifier.width(75.dp)
                    )
                    Slider(
                        value = customFeeRate.toFloatOrNull()?.coerceIn(1f, 100f) ?: 10f,
                        onValueChange = { onCustomFeeRateChange(it.toInt().toString()) },
                        valueRange = 1f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryCyan,
                            activeTrackColor = PrimaryCyan
                        )
                    )
                }
            }
        }

        // ── 4. Collapsible OP_RETURN / Memo (Not applicable for MWEB) ──
        if (!isMwebSource) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clickable { showMemo = !showMemo }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showMemo) "Memo / OP_RETURN" else "+ Add Memo (OP_RETURN)",
                    fontSize = 12.sp,
                    color = if (showMemo) TextHighEmphasis else PrimaryCyan,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showMemo) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (showMemo) TextMuted else PrimaryCyan,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = showMemo) {
                OutlinedTextField(
                    value = opReturnData,
                    onValueChange = onOpReturnChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    placeholder = {
                        Text("Hex or text memo (max 80 bytes)", color = TextMuted, fontSize = 12.sp)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceContainer,
                        unfocusedContainerColor = SurfaceContainer,
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── 5. Primary Action Button ──
        Button(
            onClick = onSend,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = isAddressValid && amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                contentColor = Color.Black
            )
        ) {
            Text(
                text = "Review Transaction",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CompactConfirmStep(
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // Large send amount display
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You are sending",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$amountJkc JKC",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHighEmphasis
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = SurfaceContainerHigh)
                Spacer(modifier = Modifier.height(14.dp))

                // Recipient
                Text(
                    text = "To Recipient",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recipientAddress,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextHighEmphasis
                )

                if (addressType != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val typeInfo = AddressValidator.getAddressTypeInfo(addressType)
                    Text(
                        text = "${typeInfo.icon} ${typeInfo.displayName}",
                        fontSize = 11.sp,
                        color = PrimaryCyan
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fee and Total rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Network Fee", fontSize = 12.sp, color = TextMuted)
                    Text("$feeJkc JKC", fontSize = 12.sp, color = TextHighEmphasis)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Deducted", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHighEmphasis)
                    Text("$totalJkc JKC", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Confirm button
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm & Send", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
        ) {
            Text("Cancel", fontSize = 14.sp)
        }
    }
}

@Composable
private fun CompactBroadcastStep() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(52.dp),
            color = PrimaryCyan,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Broadcasting Transaction...",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextHighEmphasis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Broadcasting to peer nodes",
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun CompactSuccessStep(
    txId: String,
    feeSatoshis: Long,
    amountSatoshis: Long,
    recipientAddress: String,
    onDone: () -> Unit
) {
    val amountJkc = String.format("%.8f", amountSatoshis / 100_000_000.0)
    val feeJkc = String.format("%.8f", feeSatoshis / 100_000_000.0)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(SecondaryEmerald.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = SecondaryEmerald,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sent!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextHighEmphasis
        )

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount", fontSize = 12.sp, color = TextMuted)
                    Text("$amountJkc JKC", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextHighEmphasis)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fee", fontSize = 12.sp, color = TextMuted)
                    Text("$feeJkc JKC", fontSize = 12.sp, color = TextHighEmphasis)
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceContainerHigh)
                Spacer(modifier = Modifier.height(10.dp))

                Text("Transaction ID", fontSize = 11.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceContainerHigh)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(txId))
                            Toast.makeText(context, "TxID copied", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = txId,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextHighEmphasis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                contentColor = Color.Black
            )
        ) {
            Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun QrScannedBanner(
    address: String,
    amount: String,
    opReturnData: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryCyan.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Scanned from QR",
                    fontSize = 12.sp,
                    color = PrimaryCyan,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Clear, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = address,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextHighEmphasis
            )
            if (amount.isNotBlank()) {
                Text(text = "Amount: $amount JKC", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}
