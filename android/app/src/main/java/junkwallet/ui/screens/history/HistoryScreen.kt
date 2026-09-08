package junkwallet.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import junkwallet.domain.model.TransactionInfo
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.theme.TertiaryAmber
import junkwallet.ui.theme.TxOutgoing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onTxClick: (String) -> Unit,
    transactions: List<TransactionInfo> = emptyList()
) {
    var selectedFilter by remember { mutableStateOf("all") }

    val filteredTransactions = when (selectedFilter) {
        "received" -> transactions.filter { !it.isSent }
        "sent" -> transactions.filter { it.isSent }
        else -> transactions
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: search */ }) {
                        Icon(Icons.Filled.Search, "Search", tint = TextMuted)
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
                .padding(horizontal = 16.dp)
        ) {
            // ── Filter Chips ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" },
                    label = { Text("All (${transactions.size})", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan.copy(alpha = 0.12f),
                        selectedLabelColor = PrimaryCyan
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                )
                FilterChip(
                    selected = selectedFilter == "received",
                    onClick = { selectedFilter = "received" },
                    label = { Text("Received", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SecondaryEmerald.copy(alpha = 0.12f),
                        selectedLabelColor = SecondaryEmerald
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                )
                FilterChip(
                    selected = selectedFilter == "sent",
                    onClick = { selectedFilter = "sent" },
                    label = { Text("Sent", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TxOutgoing.copy(alpha = 0.12f),
                        selectedLabelColor = TxOutgoing
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Transaction List ──
            if (filteredTransactions.isEmpty()) {
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
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Text(
                            text = "Send or receive JKC to see them here",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTransactions) { tx ->
                        TransactionItem(
                            tx = tx,
                            onClick = { onTxClick(tx.txid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    tx: TransactionInfo,
    onClick: () -> Unit
) {
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
            // Direction icon
            Icon(
                imageVector = if (tx.isSent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = if (tx.isSent) TxOutgoing else SecondaryEmerald,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (tx.isSent) TxOutgoing.copy(alpha = 0.12f)
                        else SecondaryEmerald.copy(alpha = 0.12f)
                    )
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tx.isSent) "Sent" else "Received",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHighEmphasis
                )
                Text(
                    text = tx.txid.take(16) + "...",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                if (tx.blockHeight != null) {
                    Text(
                        text = "Block #${tx.blockHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val amount = tx.netAmount
                Text(
                    text = "${if (tx.isSent) "-" else "+"}${formatAmount(amount)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tx.isSent) TxOutgoing else SecondaryEmerald
                )
                Text(
                    text = when {
                        tx.confirmed -> "Confirmed"
                        tx.isPending -> "Pending"
                        else -> "Unknown"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        tx.confirmed -> SecondaryEmerald
                        tx.isPending -> TertiaryAmber
                        else -> TextMuted
                    }
                )
            }
        }
    }
}

private fun formatAmount(satoshis: Long): String {
    val jkc = satoshis.toDouble() / 100_000_000.0
    return String.format("%.8f JKC", jkc)
}
