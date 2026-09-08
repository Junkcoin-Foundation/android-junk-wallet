package junkwallet.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.NetworkType
import junkwallet.domain.wallet.AddressValidator
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.NetworkMainnet
import junkwallet.ui.theme.NetworkTestnet
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onKeyVault: () -> Unit,
    onLock: () -> Unit,
    currentNetwork: NetworkType = NetworkType.MAINNET,
    onNetworkChanged: (NetworkType) -> Unit = {},
    defaultAddressType: AddressType = AddressType.P2PKH,
    onAddressTypeChanged: (AddressType) -> Unit = {},
    biometricEnabled: Boolean = false,
    onBiometricChanged: (Boolean) -> Unit = {},
    hasPin: Boolean = false,
    onPinSetup: (String) -> Unit = {},
    onPinRemove: () -> Unit = {}
) {
    var showNetworkMenu by remember { mutableStateOf(false) }
    var showAddressTypeMenu by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showPinDialog) {
        PinSetupDialog(
            hasPin = hasPin,
            onDismiss = { showPinDialog = false },
            onPinSet = { pin ->
                onPinSetup(pin)
                showPinDialog = false
            },
            onPinRemove = {
                onPinRemove()
                showPinDialog = false
            }
        )
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Security Section ──
            SectionHeader("Security")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column {
                    // Fingerprint toggle
                    SettingToggle(
                        title = "Fingerprint Unlock",
                        subtitle = "Use fingerprint to unlock wallet",
                        checked = biometricEnabled,
                        onCheckedChange = onBiometricChanged
                    )

                    // PIN Lock
                    SettingClickable(
                        title = "PIN Lock",
                        subtitle = if (hasPin) "Change or remove PIN" else "Set up PIN for quick unlock",
                        onClick = { showPinDialog = true }
                    )

                    // Key Vault
                    SettingClickable(
                        title = "Key Vault",
                        subtitle = "Export or import private keys",
                        onClick = onKeyVault
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Address Type Section ──
            SectionHeader("Address Type")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column {
                    // Address type selector
                    Box {
                        SettingClickable(
                            title = "Default Address Type",
                            subtitle = "Choose which address type to generate",
                            trailing = {
                                Text(
                                    text = when (defaultAddressType) {
                                        AddressType.P2PKH -> "Legacy"
                                        AddressType.P2SH_P2WPKH -> "Wrapped SegWit"
                                        AddressType.P2WPKH -> "Native SegWit"
                                        AddressType.P2TR -> "Taproot"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when (defaultAddressType) {
                                        AddressType.P2PKH -> TextMuted
                                        AddressType.P2SH_P2WPKH -> SecondaryEmerald
                                        AddressType.P2WPKH -> PrimaryCyan
                                        AddressType.P2TR -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                            },
                            onClick = { showAddressTypeMenu = true }
                        )

                        DropdownMenu(
                            expanded = showAddressTypeMenu,
                            onDismissRequest = { showAddressTypeMenu = false }
                        ) {
                            AddressType.entries.forEach { type ->
                                val typeInfo = AddressValidator.getAddressTypeInfo(type)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text(
                                                    text = "${typeInfo.icon} ${typeInfo.displayName}",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = typeInfo.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextMuted
                                                )
                                            }
                                            if (type == defaultAddressType) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(Icons.Filled.Check, "Selected", tint = PrimaryCyan)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onAddressTypeChanged(type)
                                        showAddressTypeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Address type info
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Fee Comparison",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        AddressType.entries.forEach { type ->
                            val typeInfo = AddressValidator.getAddressTypeInfo(type)
                            val feeLevelText = when (typeInfo.feeLevel) {
                                AddressValidator.FeeLevel.HIGH -> "High"
                                AddressValidator.FeeLevel.MEDIUM -> "Medium"
                                AddressValidator.FeeLevel.LOW -> "Low"
                                AddressValidator.FeeLevel.LOWEST -> "Lowest"
                            }
                            val feeLevelColor = when (typeInfo.feeLevel) {
                                AddressValidator.FeeLevel.HIGH -> ErrorCrimson
                                AddressValidator.FeeLevel.MEDIUM -> TextMuted
                                AddressValidator.FeeLevel.LOW -> SecondaryEmerald
                                AddressValidator.FeeLevel.LOWEST -> PrimaryCyan
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${typeInfo.icon} ${typeInfo.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (type == defaultAddressType) PrimaryCyan else TextMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = feeLevelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = feeLevelColor,
                                    fontWeight = if (type == defaultAddressType) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Network Section ──
            SectionHeader("Network")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column {
                    // Network selector
                    Box {
                        SettingClickable(
                            title = "Network",
                            subtitle = when (currentNetwork) {
                                NetworkType.MAINNET -> "Mainnet · junk-api.s3na.xyz"
                                NetworkType.TESTNET -> "Testnet · jkc-testnet-api.s3na.xyz"
                            },
                            trailing = {
                                Text(
                                    text = if (currentNetwork == NetworkType.MAINNET) "Mainnet" else "Testnet",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (currentNetwork == NetworkType.MAINNET) NetworkMainnet else NetworkTestnet
                                )
                            },
                            onClick = { showNetworkMenu = true }
                        )

                        DropdownMenu(
                            expanded = showNetworkMenu,
                            onDismissRequest = { showNetworkMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Mainnet")
                                        if (currentNetwork == NetworkType.MAINNET) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Filled.Check, "Selected", tint = PrimaryCyan)
                                        }
                                    }
                                },
                                onClick = {
                                    onNetworkChanged(NetworkType.MAINNET)
                                    showNetworkMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Testnet")
                                        if (currentNetwork == NetworkType.TESTNET) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Filled.Check, "Selected", tint = PrimaryCyan)
                                        }
                                    }
                                },
                                onClick = {
                                    onNetworkChanged(NetworkType.TESTNET)
                                    showNetworkMenu = false
                                }
                            )
                        }
                    }

                    // Explorer link
                    SettingClickable(
                        title = "Block Explorer",
                        subtitle = "explorer.junk-coin.com",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://explorer.junk-coin.com"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── About Section ──
            SectionHeader("About")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Version", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("1.0.0", style = MaterialTheme.typography.bodyMedium, color = TextHighEmphasis)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Network", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            if (currentNetwork == NetworkType.MAINNET) "Mainnet" else "Testnet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHighEmphasis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Coin Type", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("JKC (Scrypt)", style = MaterialTheme.typography.bodyMedium, color = TextHighEmphasis)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Address Types", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("P2PKH, P2SH-P2WPKH, P2WPKH, P2TR", style = MaterialTheme.typography.bodySmall, color = TextHighEmphasis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Lock Wallet ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLock() },
                colors = CardDefaults.cardColors(containerColor = ErrorCrimson.copy(alpha = 0.12f)),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        text = "Lock Wallet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ErrorCrimson
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = TextHighEmphasis
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextHighEmphasis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryCyan,
                checkedTrackColor = PrimaryCyan.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingClickable(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextHighEmphasis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                "Open",
                tint = TextMuted
            )
        }
    }
}

@Composable
private fun PinSetupDialog(
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit,
    onPinRemove: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) } // 0 = enter new pin, 1 = confirm pin

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainer,
        title = {
            Text(
                text = if (step == 0) "Set PIN" else "Confirm PIN",
                color = TextHighEmphasis
            )
        },
        text = {
            Column {
                if (hasPin && step == 0) {
                    Text(
                        text = "Enter new 4-6 digit PIN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else if (step == 0) {
                    Text(
                        text = "Enter a 4-6 digit PIN for quick unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = if (step == 0) pin else confirmPin,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            if (step == 0) pin = value else confirmPin = value
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = SurfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text("Enter PIN", color = TextMuted) }
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorCrimson
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (hasPin) {
                    TextButton(onClick = onPinRemove) {
                        Text("Remove PIN", color = ErrorCrimson)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
                TextButton(
                    onClick = {
                        if (step == 0) {
                            if (pin.length < 4) {
                                error = "PIN must be at least 4 digits"
                            } else {
                                step = 1
                            }
                        } else {
                            if (pin != confirmPin) {
                                error = "PINs do not match"
                                step = 0
                                pin = ""
                                confirmPin = ""
                            } else {
                                onPinSet(pin)
                            }
                        }
                    }
                ) {
                    Text("OK", color = PrimaryCyan)
                }
            }
        },
        dismissButton = null
    )
}


