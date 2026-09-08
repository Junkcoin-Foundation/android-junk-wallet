package junkwallet.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import junkwallet.domain.model.TransactionInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export transaction history to CSV format.
 */
object CsvExporter {

    /**
     * Export transactions to a CSV file and return a share intent.
     *
     * @param context Android context
     * @param transactions List of transactions to export
     * @param address The wallet address
     * @return Intent to share the CSV file, or null on error
     */
    fun exportTransactions(
        context: Context,
        transactions: List<TransactionInfo>,
        address: String
    ): Intent? {
        return try {
            val csvContent = generateCsv(transactions, address)
            val fileName = "junkcoin_transactions_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            file.writeText(csvContent)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Junkcoin Transaction History")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate CSV content from transactions.
     */
    private fun generateCsv(transactions: List<TransactionInfo>, address: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return buildString {
            // Header
            appendLine("txid,type,amount_jkc,amount_sats,fee_jkc,fee_sats,status,block_height,timestamp")

            // Transactions
            for (tx in transactions) {
                val type = if (tx.isSent) "sent" else "received"
                val amountJkc = String.format("%.8f", tx.netAmount / 100_000_000.0)
                val amountSats = tx.netAmount
                val feeJkc = tx.fee?.let { String.format("%.8f", it / 100_000_000.0) } ?: ""
                val feeSats = tx.fee?.toString() ?: ""
                val status = if (tx.confirmed) "confirmed" else "pending"
                val blockHeight = tx.blockHeight?.toString() ?: ""
                val timestamp = tx.blockTime?.let { sdf.format(Date(it * 1000)) } ?: ""

                appendLine("${tx.txid},$type,$amountJkc,$amountSats,$feeJkc,$feeSats,$status,$blockHeight,$timestamp")
            }
        }
    }

    /**
     * Export UTXOs to CSV.
     */
    fun exportUtxos(
        context: Context,
        utxos: List<junkwallet.domain.model.UtxoInfo>,
        address: String
    ): Intent? {
        return try {
            val csvContent = buildString {
                appendLine("txid,vout,value_jkc,value_sats,confirmed,block_height")

                for (utxo in utxos) {
                    val valueJkc = String.format("%.8f", utxo.value / 100_000_000.0)
                    val confirmed = if (utxo.confirmed) "yes" else "no"
                    val blockHeight = utxo.blockHeight?.toString() ?: ""

                    appendLine("${utxo.txid},${utxo.vout},$valueJkc,${utxo.value},$confirmed,$blockHeight")
                }
            }

            val fileName = "junkcoin_utxos_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            file.writeText(csvContent)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Junkcoin UTXO Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }
}
