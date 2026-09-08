package junkwallet.ui.screens.history

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import junkwallet.domain.model.NetworkType
import junkwallet.domain.model.TransactionInfo
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.NetworkMainnet
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SecondaryEmerald
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.theme.TertiaryAmber
import junkwallet.ui.theme.TxOutgoing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDetailScreen(
    tx: TransactionInfo,
    onBack: () -> Unit,
    myAddress: String = "",
    network: NetworkType = NetworkType.MAINNET
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val explorerUrl = when (network) {
        NetworkType.MAINNET -> "https://explorer.junk-coin.com/tx/${tx.txid}"
        NetworkType.TESTNET -> "https://explorer.junk-coin.com/testnet/tx/${tx.txid}"
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Status Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (tx.isSent) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = if (tx.isSent) TxOutgoing else SecondaryEmerald,
                        modifier = Modifier.size(56.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (tx.isSent) "Sent" else "Received",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextHighEmphasis,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${if (tx.isSent) "-" else "+"}${formatAmount(tx.netAmount)} JKC",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (tx.isSent) TxOutgoing else SecondaryEmerald,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            tx.confirmed -> "Confirmed"
                            tx.isPending -> "Pending (0 confirmations)"
                            else -> "Unknown"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            tx.confirmed -> SecondaryEmerald
                            tx.isPending -> TertiaryAmber
                            else -> TextMuted
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Details Card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow(
                        label = "Transaction ID",
                        value = tx.txid,
                        onCopy = { clipboard.setText(AnnotatedString(tx.txid)) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (tx.blockHeight != null) {
                        DetailRow(
                            label = "Block",
                            value = "#${tx.blockHeight}"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    if (tx.blockTime != null) {
                        DetailRow(
                            label = "Timestamp",
                            value = formatTimestamp(tx.blockTime)
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    if (tx.fee != null) {
                        DetailRow(
                            label = "Fee",
                            value = "${formatAmount(tx.fee)} JKC"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    DetailRow(
                        label = "Sent",
                        value = "${formatAmount(tx.sent)} JKC"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    DetailRow(
                        label = "Received",
                        value = "${formatAmount(tx.received)} JKC"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Explorer Button ──
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(explorerUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PrimaryCyan
                )
            ) {
                Icon(
                    Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View in Explorer",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Copy & Share ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { clipboard.setText(AnnotatedString(tx.txid)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy TXID")
                }

                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, explorerUrl)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Transaction"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = TextHighEmphasis
            )
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        "Copy",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun formatAmount(satoshis: Long): String {
    val jkc = satoshis.toDouble() / 100_000_000.0
    return String.format("%.8f", jkc)
}

private fun formatTimestamp(epochSeconds: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochSeconds * 1000))
}
