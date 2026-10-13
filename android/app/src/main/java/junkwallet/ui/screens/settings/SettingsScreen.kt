package junkwallet.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.NetworkType
import junkwallet.domain.wallet.AddressValidator
import junkwallet.ui.components.PinBoxesInput
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.NetworkMainnet
import junkwallet.ui.theme.NetworkTestnet
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TertiaryAmber
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import kotlinx.coroutines.launch

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
    supportedAddressTypes: List<AddressType> = AddressType.entries,
    biometricEnabled: Boolean = false,
    onBiometricChanged: (Boolean) -> Unit = {},
    hasPin: Boolean = false,
    onPinSetup: (String) -> Unit = {},
    onPinRemove: () -> Unit = {},
    onChangePassword: suspend (currentPassword: String, newPassword: String) -> Boolean = { _, _ -> false },
    versionName: String = "1.0.0"
) {
    var showNetworkMenu by remember { mutableStateOf(false) }
    var showAddressTypeMenu by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onPasswordChanged = { showChangePasswordDialog = false },
            onChangePassword = onChangePassword
        )
    }

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
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = TextHighEmphasis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── 1. Security Group ──
            SectionTitle("Security")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column {
                    CompactSettingToggle(
                        icon = Icons.Filled.Fingerprint,
                        iconTint = PrimaryCyan,
                        title = "Biometric Unlock",
                        checked = biometricEnabled,
                        onCheckedChange = onBiometricChanged
                    )
                    Divider()
                    CompactSettingRow(
                        icon = Icons.Filled.Password,
                        iconTint = SecondaryEmerald,
                        title = "Change Password",
                        onClick = { showChangePasswordDialog = true }
                    )
                    Divider()
                    CompactSettingRow(
                        icon = Icons.Filled.Pin,
                        iconTint = TertiaryAmber,
                        title = "PIN Code",
                        trailingText = if (hasPin) "Active" else "Off",
                        trailingTextColor = if (hasPin) SecondaryEmerald else TextMuted,
                        onClick = { showPinDialog = true }
                    )
                    Divider()
                    CompactSettingRow(
                        icon = Icons.Filled.Key,
                        iconTint = PrimaryCyan,
                        title = "Key Vault (Private Keys)",
                        onClick = onKeyVault
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 2. Preferences & Network Group ──
            SectionTitle("Preferences")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column {
                    // Address type
                    Box {
                        val typeLabel = when (defaultAddressType) {
                            AddressType.P2PKH -> "Legacy"
                            AddressType.P2SH_P2WPKH -> "Wrapped"
                            AddressType.P2WPKH -> "SegWit"
                            AddressType.P2TR -> "Taproot"
                            AddressType.MWEB -> "MWEB"
                        }

                        CompactSettingRow(
                            icon = Icons.Filled.Tag,
                            iconTint = PrimaryCyan,
                            title = "Address Format",
                            trailingText = typeLabel,
                            trailingTextColor = PrimaryCyan,
                            onClick = { showAddressTypeMenu = true }
                        )

                        DropdownMenu(
                            expanded = showAddressTypeMenu,
                            onDismissRequest = { showAddressTypeMenu = false }
                        ) {
                            supportedAddressTypes.forEach { type ->
                                val info = AddressValidator.getAddressTypeInfo(type)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${info.icon} ${info.displayName}",
                                                modifier = Modifier.weight(1f),
                                                fontSize = 14.sp
                                            )
                                            if (type == defaultAddressType) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = PrimaryCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
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

                    Divider()

                    // Network selector
                    Box {
                        CompactSettingRow(
                            icon = Icons.Filled.Language,
                            iconTint = if (currentNetwork == NetworkType.MAINNET) NetworkMainnet else NetworkTestnet,
                            title = "Network",
                            trailingText = if (currentNetwork == NetworkType.MAINNET) "Mainnet" else "Testnet",
                            trailingTextColor = if (currentNetwork == NetworkType.MAINNET) NetworkMainnet else NetworkTestnet,
                            onClick = { showNetworkMenu = true }
                        )

                        DropdownMenu(
                            expanded = showNetworkMenu,
                            onDismissRequest = { showNetworkMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Mainnet", modifier = Modifier.weight(1f))
                                        if (currentNetwork == NetworkType.MAINNET) {
                                            Icon(Icons.Filled.Check, null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
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
                                        Text("Testnet", modifier = Modifier.weight(1f))
                                        if (currentNetwork == NetworkType.TESTNET) {
                                            Icon(Icons.Filled.Check, null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
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

                    Divider()

                    // Explorer
                    CompactSettingRow(
                        icon = Icons.Filled.OpenInNew,
                        iconTint = TextMuted,
                        title = "Block Explorer",
                        trailingIcon = Icons.Filled.OpenInNew,
                        onClick = {
                            val url = if (currentNetwork == NetworkType.TESTNET) {
                                "https://explorer.junk-coin.com/testnet"
                            } else {
                                "https://explorer.junk-coin.com"
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 3. About & System Group ──
            SectionTitle("About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
            ) {
                Column {
                    CompactSettingRow(
                        icon = Icons.Filled.Info,
                        iconTint = TextMuted,
                        title = "Version",
                        trailingText = "v$versionName",
                        trailingTextColor = TextHighEmphasis,
                        hasChevron = false,
                        onClick = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 4. Lock Wallet Button ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLock() },
                colors = CardDefaults.cardColors(containerColor = ErrorCrimson.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = ErrorCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lock Wallet",
                        color = ErrorCrimson,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = SurfaceContainerHigh,
        thickness = 0.8.dp,
        modifier = Modifier.padding(start = 54.dp)
    )
}

@Composable
private fun CompactSettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    trailingText: String? = null,
    trailingTextColor: Color = TextMuted,
    trailingIcon: ImageVector? = null,
    hasChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            fontSize = 14.sp,
            color = TextHighEmphasis,
            modifier = Modifier.weight(1f)
        )

        if (trailingText != null) {
            Text(
                text = trailingText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = trailingTextColor
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        } else if (hasChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CompactSettingToggle(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            fontSize = 14.sp,
            color = TextHighEmphasis,
            modifier = Modifier.weight(1f)
        )

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
private fun PinSetupDialog(
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit,
    onPinRemove: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }

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
                Text(
                    text = if (hasPin && step == 0) "Enter new 4-6 digit PIN"
                    else if (step == 0) "Enter a 4-6 digit PIN for quick unlock"
                    else "Re-enter your PIN to confirm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                key(step) {
                    PinBoxesInput(
                        pin = if (step == 0) pin else confirmPin,
                        onPinChange = { value ->
                            if (step == 0) pin = value else confirmPin = value
                            error = null
                        },
                        isError = error != null,
                        autoFocus = true
                    )
                }

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

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit,
    onChangePassword: suspend (currentPassword: String, newPassword: String) -> Boolean = { _, _ -> false }
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val dialogScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainer,
        title = {
            Text(
                text = when (step) {
                    0 -> "Current Password"
                    1 -> "New Password"
                    else -> "Confirm New Password"
                },
                color = TextHighEmphasis
            )
        },
        text = {
            Column {
                Text(
                    text = when (step) {
                        0 -> "Enter your current password"
                        1 -> "Enter a new password (min 8 characters)"
                        else -> "Confirm your new password"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = when (step) {
                        0 -> currentPassword
                        1 -> newPassword
                        else -> confirmPassword
                    },
                    onValueChange = { value ->
                        when (step) {
                            0 -> currentPassword = value
                            1 -> newPassword = value
                            else -> confirmPassword = value
                        }
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = SurfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = error != null,
                    placeholder = {
                        Text(
                            when (step) {
                                0 -> "Enter current password"
                                1 -> "Enter new password"
                                else -> "Confirm new password"
                            },
                            color = TextMuted
                        )
                    }
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
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        when (step) {
                            0 -> {
                                if (currentPassword.isBlank()) {
                                    error = "Please enter current password"
                                } else {
                                    step = 1
                                }
                            }
                            1 -> {
                                if (newPassword.length < 8) {
                                    error = "Password must be at least 8 characters"
                                } else {
                                    step = 2
                                }
                            }
                            2 -> {
                                if (newPassword != confirmPassword) {
                                    error = "Passwords do not match"
                                    step = 1
                                    newPassword = ""
                                    confirmPassword = ""
                                } else if (!busy) {
                                    busy = true
                                    dialogScope.launch {
                                        val success = onChangePassword(currentPassword, newPassword)
                                        busy = false
                                        if (success) {
                                            onPasswordChanged()
                                        } else {
                                            error = "Current password is incorrect"
                                            step = 0
                                            currentPassword = ""
                                            newPassword = ""
                                            confirmPassword = ""
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(if (busy) "…" else "OK", color = PrimaryCyan)
                }
            }
        },
        dismissButton = null
    )
}
