package junkwallet.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import junkwallet.domain.model.AddressType
import junkwallet.domain.model.FeeEstimates
import junkwallet.domain.model.TransactionInfo
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.NetworkMainnet
import junkwallet.ui.theme.NetworkTestnet
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.SurfaceContainerLow
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
    onRefresh: () -> Unit = {},
    onNetworkToggle: () -> Unit = {},
    onAddressTypeChanged: (AddressType) -> Unit = {},
    onTxClick: (String) -> Unit,
    confirmedBalance: Long = 0,
    unconfirmedBalance: Long = 0,
    address: String = "",
    blockHeight: Int = 0,
    networkName: String = "Mainnet",
    defaultAddressType: AddressType = AddressType.P2PKH,
    isLoading: Boolean = false,
    transactions: List<TransactionInfo> = emptyList(),
    fiatPriceUsd: Double = 0.0,
    feeEstimates: FeeEstimates = FeeEstimates(),
    utxoCount: Int = 0,
    error: String? = null,
    onErrorDismiss: () -> Unit = {},
    lastSyncedText: String = "Never"
) {
    val clipboard = LocalClipboardManager.current
    val isMainnet = networkName.lowercase().contains("main")
    val networkColor = if (isMainnet) NetworkMainnet else NetworkTestnet

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Wallet,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Junkcoin",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHighEmphasis,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Network badge pill — tap to toggle mainnet/testnet
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable(onClick = onNetworkToggle)
                            .background(networkColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(networkColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = networkName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = networkColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = "Switch network",
                            tint = networkColor,
                            modifier = Modifier.size(12.dp)
                        )
                        if (blockHeight > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "#$blockHeight",
                                style = MaterialTheme.typography.labelSmall,
                                color = networkColor.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ── Balance Card ──
            item {
                BalanceCard(
                    confirmedBalance = confirmedBalance,
                    unconfirmedBalance = unconfirmedBalance,
                    address = address,
                    fiatPriceUsd = fiatPriceUsd,
                    defaultAddressType = defaultAddressType,
                    onCopyAddress = { clipboard.setText(AnnotatedString(address)) },
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
                                .padding(12.dp),
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
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelSmall,
                                color = TertiaryAmber,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .clickable(onClick = onErrorDismiss)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── Action Buttons ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSend,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward, "Send",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF003640)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send", color = Color(0xFF003640), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onReceive,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, SurfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward, "Receive",
                            modifier = Modifier.size(18.dp),
                            tint = SecondaryEmerald
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receive", color = SecondaryEmerald, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Mempool & Fee Health ──
            item {
                MempoolFeeCard(
                    feeEstimates = feeEstimates,
                    isLoading = isLoading,
                    utxoCount = utxoCount,
                    lastSyncedText = lastSyncedText
                )
            }

            // ── Recent Activity header ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextHighEmphasis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View all →",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryCyan,
                        modifier = Modifier.clickable(onClick = onHistory)
                    )
                }
            }

            // ── Transactions ──
            if (transactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Wallet,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHighEmphasis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Send or receive JKC to get started",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                items(transactions.take(10)) { tx ->
                    TransactionRow(tx = tx, onClick = { onTxClick(tx.txid) })
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ── Balance Card ──
@Composable
private fun BalanceCard(
    confirmedBalance: Long,
    unconfirmedBalance: Long,
    address: String,
    fiatPriceUsd: Double,
    defaultAddressType: AddressType = AddressType.P2PKH,
    onCopyAddress: () -> Unit,
    onAddressTypeChanged: (AddressType) -> Unit = {}
) {
    var showTypeDropdown by remember { mutableStateOf(false) }

    val typeLabel = when (defaultAddressType) {
        AddressType.P2PKH -> "Legacy"
        AddressType.P2SH_P2WPKH -> "Wrapped SegWit"
        AddressType.P2WPKH -> "Native SegWit"
        AddressType.P2TR -> "Taproot"
    }
    val typeShort = when (defaultAddressType) {
        AddressType.P2PKH -> "P2PKH"
        AddressType.P2SH_P2WPKH -> "P2SH"
        AddressType.P2WPKH -> "P2WPKH"
        AddressType.P2TR -> "P2TR"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TOTAL BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatBalance(confirmedBalance + unconfirmedBalance),
                style = MaterialTheme.typography.displayMedium,
                color = TextHighEmphasis,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            if (unconfirmedBalance != 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(TertiaryAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(TertiaryAmber)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${if (unconfirmedBalance > 0) "+" else ""}${formatBalance(unconfirmedBalance)} pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = TertiaryAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (fiatPriceUsd > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                val jkcAmount = (confirmedBalance + unconfirmedBalance) / 100_000_000.0
                Text(
                    text = String.format("≈ $%.4f USD", jkcAmount * fiatPriceUsd),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wallet type dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(SurfaceContainerHigh)
                        .clickable { showTypeDropdown = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.Wallet,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wallet Type",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = "$typeLabel ($typeShort)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHighEmphasis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Change type",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showTypeDropdown,
                    onDismissRequest = { showTypeDropdown = false }
                ) {
                    AddressType.entries.forEach { type ->
                        val label = when (type) {
                            AddressType.P2PKH -> "Legacy (P2PKH)"
                            AddressType.P2SH_P2WPKH -> "Wrapped SegWit (P2SH)"
                            AddressType.P2WPKH -> "Native SegWit (P2WPKH)"
                            AddressType.P2TR -> "Taproot (P2TR)"
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (type == defaultAddressType) PrimaryCyan else TextHighEmphasis,
                                    fontWeight = if (type == defaultAddressType) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onAddressTypeChanged(type)
                                showTypeDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address row with copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(SurfaceContainerHigh)
                    .clickable(onClick = onCopyAddress)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = address.ifEmpty { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ContentCopy, "Copy Address",
                    tint = PrimaryCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ── Mempool & Fee Health Card ──
@Composable
private fun MempoolFeeCard(
    feeEstimates: FeeEstimates,
    isLoading: Boolean,
    utxoCount: Int,
    lastSyncedText: String = "Never"
) {
    val hasFees = feeEstimates.priorityRate > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isLoading) TertiaryAmber else SecondaryEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLoading) "Syncing…" else "Synced · $lastSyncedText",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (utxoCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(SurfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$utxoCount UTXOs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = SurfaceContainerHigh
            )

            // Fee tiers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeeTierColumn(
                    label = "ECONOMY",
                    value = if (hasFees) String.format("%.1f sat/vB", feeEstimates.slowRate) else "— sat/vB",
                    color = TextMuted
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(SurfaceContainerHigh)
                )
                FeeTierColumn(
                    label = "STANDARD",
                    value = if (hasFees) String.format("%.1f sat/vB", feeEstimates.standardRate) else "— sat/vB",
                    color = PrimaryCyan,
                    highlighted = true
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(SurfaceContainerHigh)
                )
                FeeTierColumn(
                    label = "PRIORITY",
                    value = if (hasFees) String.format("%.1f sat/vB", feeEstimates.priorityRate) else "— sat/vB",
                    color = TertiaryAmber
                )
            }
        }
    }
}

@Composable
private fun FeeTierColumn(
    label: String,
    value: String,
    color: Color,
    highlighted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) color else TextMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Transaction Row ──
@Composable
private fun TransactionRow(tx: TransactionInfo, onClick: () -> Unit) {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (tx.isSent) TxOutgoing.copy(alpha = 0.12f)
                        else SecondaryEmerald.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (tx.isSent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = if (tx.isSent) TxOutgoing else SecondaryEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tx.isSent) "Sent" else "Received",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHighEmphasis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = tx.txid.take(14) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMonospace,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (tx.isSent) "-" else "+"}${formatBalance(tx.netAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tx.isSent) TxOutgoing else SecondaryEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(
                            if (tx.confirmed) SecondaryEmerald.copy(alpha = 0.10f)
                            else TertiaryAmber.copy(alpha = 0.10f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (tx.confirmed) "Confirmed" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tx.confirmed) SecondaryEmerald else TertiaryAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun formatBalance(satoshis: Long): String {
    val jkc = satoshis.toDouble() / 100_000_000.0
    return String.format("%.8f JKC", jkc)
}



