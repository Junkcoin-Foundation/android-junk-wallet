package junkwallet.ui.screens.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import junkwallet.R
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.FeeEstimates
import junkwallet.domain.model.TransactionInfo
import junkwallet.domain.model.WalletAccount
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.NetworkMainnet
import junkwallet.ui.theme.NetworkTestnet
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMonospace
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.theme.TertiaryAmber
import junkwallet.ui.theme.TxOutgoing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onScanQr: () -> Unit = {},
    onKeyVault: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onNetworkToggle: () -> Unit = {},
    onAddressTypeChanged: (AddressType) -> Unit = {},
    onAccountSelected: (String) -> Unit = {},
    onAccountRename: (String, String) -> Unit = { _, _ -> },
    onAccountCreate: (String) -> Unit = {},
    onAccountDelete: (String) -> Unit = {},
    accounts: List<WalletAccount> = emptyList(),
    activeAccountId: String = "",
    onTxClick: (String) -> Unit,
    confirmedBalance: Long = 0,
    unconfirmedBalance: Long = 0,
    address: String = "",
    allAddresses: Map<String, String> = emptyMap(),
    blockHeight: Int = 0,
    networkName: String = "Mainnet",
    defaultAddressType: AddressType = AddressType.P2PKH,
    mwebBalance: Long = 0L,
    isLoading: Boolean = false,
    transactions: List<TransactionInfo> = emptyList(),
    fiatPriceUsd: Double = 0.0,
    feeEstimates: FeeEstimates = FeeEstimates(),
    utxoCount: Int = 0,
    error: String? = null,
    onErrorDismiss: () -> Unit = {},
    lastSyncedText: String = "Never"
) {
    val context = LocalContext.current
    val isMainnet = networkName.lowercase().contains("main")
    val displayedAddress = if (defaultAddressType == AddressType.MWEB) {
        allAddresses[AddressType.MWEB.name] ?: address
    } else {
        address
    }
    val networkColor = if (isMainnet) NetworkMainnet else NetworkTestnet

    var showAccountMenu by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<WalletAccount?>(null) }
    val activeAccount = accounts.find { it.id == activeAccountId }

    fun copyAddress(addr: String) {
        if (addr.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Junkcoin Address", addr)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_junkcoin_logo),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Compact Account Selector Pill
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { showAccountMenu = true }
                                    .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = activeAccount?.name ?: "Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextHighEmphasis,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "Select account",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                accounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = account.name,
                                                    fontWeight = if (account.id == activeAccountId) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = account.defaultAddressType.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMuted
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    showAccountMenu = false
                                                    accountToDelete = account
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete ${account.name}",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onAccountSelected(account.id)
                                            showAccountMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "+ Add Account",
                                            color = PrimaryCyan
                                        )
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onAccountCreate("Account ${accounts.size + 1}")
                                    }
                                )
                            }
                        }

                        accountToDelete?.let { target ->
                            AlertDialog(
                                onDismissRequest = { accountToDelete = null },
                                title = { Text("Delete ${target.name}?") },
                                text = {
                                    Text(
                                        if (accounts.size <= 1) {
                                            "This is your only account. Deleting it wipes this wallet from the device."
                                        } else {
                                            "This account's key and addresses are removed from this device."
                                        }
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        accountToDelete = null
                                        onAccountDelete(target.id)
                                    }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { accountToDelete = null }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                },
                actions = {
                    // Global Network Pill (Compact status indicator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable(onClick = onNetworkToggle)
                            .background(networkColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(networkColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = networkName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = networkColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = "Switch network",
                            tint = networkColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Refresh
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PrimaryCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = TextMuted)
                        }
                    }

                    // Settings
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, "Settings", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // ── Hero Balance Card (Compact & Minimalist) ──
            item {
                BalanceHeroCard(
                    confirmedBalance = confirmedBalance,
                    unconfirmedBalance = unconfirmedBalance,
                    address = displayedAddress,
                    allAddresses = allAddresses,
                    fiatPriceUsd = fiatPriceUsd,
                    defaultAddressType = defaultAddressType,
                    mwebBalance = mwebBalance,
                    onCopyAddress = { copyAddress(displayedAddress) },
                    onAddressTypeChanged = onAddressTypeChanged
                )
            }

            // ── Error Banner ──
            if (error != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = TertiaryAmber.copy(alpha = 0.12f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.titleMedium,
                                color = TertiaryAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = TertiaryAmber,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.titleMedium,
                                color = TertiaryAmber,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onErrorDismiss)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Quick Action Bar (3 Compact Circle Actions) ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickActionButton(
                        icon = Icons.Filled.ArrowUpward,
                        label = "Send",
                        containerColor = PrimaryCyan,
                        contentColor = Color(0xFF003640),
                        onClick = onSend
                    )
                    QuickActionButton(
                        icon = Icons.Filled.ArrowDownward,
                        label = "Receive",
                        containerColor = SecondaryEmerald.copy(alpha = 0.18f),
                        contentColor = SecondaryEmerald,
                        onClick = onReceive
                    )
                    QuickActionButton(
                        icon = Icons.Filled.QrCodeScanner,
                        label = "Scan",
                        containerColor = SurfaceContainerHigh,
                        contentColor = PrimaryCyan,
                        onClick = onScanQr
                    )
                }
            }

            // ── Activity Section Header ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHighEmphasis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onHistory)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryCyan
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View all",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // ── Transactions List (Compact) ──
            if (transactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Wallet,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                items(transactions.take(8)) { tx ->
                    CompactTransactionRow(tx = tx, onClick = { onTxClick(tx.txid) })
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ── Hero Balance Card (Compact & Clean) ──
@Composable
private fun BalanceHeroCard(
    confirmedBalance: Long,
    unconfirmedBalance: Long,
    address: String,
    allAddresses: Map<String, String> = emptyMap(),
    fiatPriceUsd: Double,
    defaultAddressType: AddressType = AddressType.P2PKH,
    mwebBalance: Long = 0L,
    onCopyAddress: () -> Unit,
    onAddressTypeChanged: (AddressType) -> Unit = {}
) {
    var showTypeDropdown by remember { mutableStateOf(false) }
    var hideBalance by remember { mutableStateOf(false) }
    val isMwebSource = defaultAddressType == AddressType.MWEB
    val totalSat = if (isMwebSource) mwebBalance else confirmedBalance + unconfirmedBalance

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main balance row with privacy eye toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (hideBalance) "••••••••" else formatBalance(totalSat),
                    style = MaterialTheme.typography.displaySmall,
                    color = TextHighEmphasis,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { hideBalance = !hideBalance },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (hideBalance) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Toggle privacy",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Pending balance pill
            if (!isMwebSource && unconfirmedBalance != 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(TertiaryAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(TertiaryAmber)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${if (unconfirmedBalance > 0) "+" else ""}${formatBalance(unconfirmedBalance)} pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = TertiaryAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Fiat valuation
            if (!hideBalance && fiatPriceUsd > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val jkcAmount = totalSat / 100_000_000.0
                Text(
                    text = String.format("≈ $%.2f USD", jkcAmount * fiatPriceUsd),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Address Pill 1-Tap
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(SurfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Type indicator / dropdown toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showTypeDropdown = true }
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = defaultAddressType.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "Change type",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .width(1.dp)
                            .background(TextMuted.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Address string + copy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onCopyAddress)
                    ) {
                        Text(
                            text = if (address.length > 14)
                                "${address.take(6)}...${address.takeLast(6)}"
                            else address.ifEmpty { "—" },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextHighEmphasis,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy address",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false }
                ) {
                    allAddresses.forEach { (typeKey, addr) ->
                        val type = try { AddressType.valueOf(typeKey) } catch (_: Exception) { null } ?: return@forEach
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = type.name,
                                        fontWeight = if (type == defaultAddressType) FontWeight.Bold else FontWeight.Normal,
                                        color = if (type == defaultAddressType) PrimaryCyan else TextHighEmphasis,
                                        modifier = Modifier.width(70.dp)
                                    )
                                    Text(
                                        text = "${addr.take(8)}...${addr.takeLast(6)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            },
                            onClick = {
                                onAddressTypeChanged(type)
                                showTypeDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Quick Action Button (Compact Circular) ──
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextHighEmphasis,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Compact Transaction Row ──
@Composable
private fun CompactTransactionRow(tx: TransactionInfo, onClick: () -> Unit) {
    val mwebBlue = Color(0xFF0284C7)
    val mwebLightBlue = Color(0xFF38BDF8)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction circle icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (tx.isMweb) mwebBlue.copy(alpha = 0.16f)
                        else if (tx.isSent) TxOutgoing.copy(alpha = 0.12f)
                        else SecondaryEmerald.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (tx.isSent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = if (tx.isMweb) mwebLightBlue else if (tx.isSent) TxOutgoing else SecondaryEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (tx.isSent) "Sent" else "Received",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tx.isMweb) mwebLightBlue else TextHighEmphasis,
                        fontWeight = FontWeight.Medium
                    )
                    if (tx.isMweb) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(mwebBlue.copy(alpha = 0.25f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "MWEB",
                                style = MaterialTheme.typography.labelSmall,
                                color = mwebLightBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = tx.txid.take(12) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMonospace,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (tx.isSent) "-" else "+"}${formatBalance(tx.netAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tx.isMweb) mwebLightBlue else if (tx.isSent) TxOutgoing else SecondaryEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (tx.isMweb) mwebLightBlue
                                else if (tx.confirmed) SecondaryEmerald
                                else TertiaryAmber
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (tx.confirmed) "Confirmed" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tx.isMweb) mwebLightBlue else if (tx.confirmed) SecondaryEmerald else TertiaryAmber,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatBalance(satoshis: Long): String {
    val jkc = satoshis.toDouble() / 100_000_000.0
    return String.format("%.4f JKC", jkc)
}
