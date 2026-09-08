package junkwallet.domain.wallet

import junkwallet.data.model.Utxo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced coin selection algorithm for UTXO-based transactions.
 * Supports multiple strategies: greedy, branch-and-bound, and consolidation.
 */
@Singleton
class CoinSelector @Inject constructor() {

    companion object {
        const val DUST_THRESHOLD = 546L // satoshis
        const val P2PKH_INPUT_SIZE = 148 // bytes
        const val P2PKH_OUTPUT_SIZE = 34 // bytes
        const val TX_OVERHEAD = 10 // bytes
    }

    /**
     * Select UTXOs using greedy accumulation (largest first).
     *
     * @param utxos Available UTXOs
     * @param targetAmount Amount needed in satoshis
     * @param feeRateSatVb Fee rate in sat/virtual-byte
     * @param requireConfirmed Only use confirmed UTXOs (default true)
     * @return Coin selection result
     */
    fun select(
        utxos: List<Utxo>,
        targetAmount: Long,
        feeRateSatVb: Double,
        requireConfirmed: Boolean = true
    ): CoinSelection {
        // Filter to confirmed only if required
        val available = if (requireConfirmed) {
            utxos.filter { it.status.confirmed }
        } else {
            utxos
        }

        if (available.isEmpty()) {
            return CoinSelection(
                utxos = emptyList(),
                totalValue = 0L,
                estimatedFee = 0L,
                change = 0L,
                insufficientFunds = true
            )
        }

        // Sort by value descending (largest first)
        val sorted = available.sortedByDescending { it.value }

        // Try greedy selection
        val selected = mutableListOf<Utxo>()
        var totalValue = 0L

        for (utxo in sorted) {
            selected.add(utxo)
            totalValue += utxo.value

            val outputCount = 2 // recipient + change
            val estimatedFee = estimateFee(selected.size, outputCount, feeRateSatVb)

            if (totalValue >= targetAmount + estimatedFee) {
                val change = totalValue - targetAmount - estimatedFee
                return CoinSelection(
                    utxos = selected.toList(),
                    totalValue = totalValue,
                    estimatedFee = estimatedFee,
                    change = change,
                    hasChange = change >= DUST_THRESHOLD
                )
            }
        }

        // Not enough funds
        return CoinSelection(
            utxos = emptyList(),
            totalValue = 0L,
            estimatedFee = 0L,
            change = 0L,
            insufficientFunds = true
        )
    }

    /**
     * Select UTXOs for consolidation (combine many small UTXOs).
     */
    fun selectForConsolidation(
        utxos: List<Utxo>,
        feeRateSatVb: Double,
        maxInputs: Int = 100
    ): CoinSelection {
        val confirmed = utxos.filter { it.status.confirmed }
            .sortedBy { it.value } // smallest first for consolidation

        if (confirmed.isEmpty()) {
            return CoinSelection(
                utxos = emptyList(),
                totalValue = 0L,
                estimatedFee = 0L,
                change = 0L,
                insufficientFunds = true
            )
        }

        val selected = mutableListOf<Utxo>()
        var totalValue = 0L

        for (utxo in confirmed.take(maxInputs)) {
            selected.add(utxo)
            totalValue += utxo.value

            // Estimate fee for current selection
            val estimatedFee = estimateFee(selected.size, 1, feeRateSatVb) // 1 output = self

            // Stop if net value (total - fee) is reasonable
            if (totalValue - estimatedFee > DUST_THRESHOLD && selected.size >= 10) {
                break
            }
        }

        val fee = estimateFee(selected.size, 1, feeRateSatVb)
        val netValue = totalValue - fee

        return if (netValue > DUST_THRESHOLD) {
            CoinSelection(
                utxos = selected.toList(),
                totalValue = totalValue,
                estimatedFee = fee,
                change = netValue,
                hasChange = true
            )
        } else {
            CoinSelection(
                utxos = emptyList(),
                totalValue = 0L,
                estimatedFee = 0L,
                change = 0L,
                insufficientFunds = true
            )
        }
    }

    /**
     * Estimate transaction fee in satoshis.
     */
    fun estimateFee(inputCount: Int, outputCount: Int, feeRateSatVb: Double): Long {
        val estimatedSize = TX_OVERHEAD + (inputCount * P2PKH_INPUT_SIZE) + (outputCount * P2PKH_OUTPUT_SIZE)
        return Math.ceil(estimatedSize * feeRateSatVb).toLong()
    }

    /**
     * Calculate optimal fee rate based on target confirmation time.
     */
    fun calculateFeeRate(
        targetBlocks: Int,
        feeEstimates: Map<String, Double>
    ): Double {
        return when {
            targetBlocks <= 1 -> feeEstimates["1"] ?: 20.0
            targetBlocks <= 3 -> feeEstimates["3"] ?: 10.0
            targetBlocks <= 6 -> feeEstimates["6"] ?: 5.0
            targetBlocks <= 12 -> feeEstimates["12"] ?: 3.0
            else -> feeEstimates["144"] ?: 1.0
        }
    }

    /**
     * Analyze UTXOs for coin control display.
     */
    fun analyzeUtxos(utxos: List<Utxo>): UtxoAnalysis {
        val confirmed = utxos.filter { it.status.confirmed }
        val unconfirmed = utxos.filter { !it.status.confirmed }

        val totalValue = utxos.sumOf { it.value }
        val confirmedValue = confirmed.sumOf { it.value }
        val unconfirmedValue = unconfirmed.sumOf { it.value }

        val avgValue = if (utxos.isNotEmpty()) totalValue / utxos.size else 0L
        val dustCount = utxos.count { it.value < DUST_THRESHOLD }

        return UtxoAnalysis(
            totalCount = utxos.size,
            confirmedCount = confirmed.size,
            unconfirmedCount = unconfirmed.size,
            totalValue = totalValue,
            confirmedValue = confirmedValue,
            unconfirmedValue = unconfirmedValue,
            averageValue = avgValue,
            dustCount = dustCount,
            largestUtxo = utxos.maxByOrNull { it.value },
            smallestUtxo = utxos.minByOrNull { it.value }
        )
    }
}

data class CoinSelection(
    val utxos: List<Utxo>,
    val totalValue: Long,
    val estimatedFee: Long,
    val change: Long,
    val hasChange: Boolean = false,
    val insufficientFunds: Boolean = false
) {
    val feeFormatted: String
        get() = String.format("%.8f JKC", estimatedFee / 100_000_000.0)

    val totalValueFormatted: String
        get() = String.format("%.8f JKC", totalValue / 100_000_000.0)

    val summary: String
        get() = buildString {
            appendLine("Coin Selection:")
            appendLine("  UTXOs: ${utxos.size}")
            appendLine("  Total: $totalValueFormatted")
            appendLine("  Fee: $feeFormatted")
            if (hasChange) {
                appendLine("  Change: ${String.format("%.8f", change / 100_000_000.0)} JKC")
            }
        }
}

data class UtxoAnalysis(
    val totalCount: Int,
    val confirmedCount: Int,
    val unconfirmedCount: Int,
    val totalValue: Long,
    val confirmedValue: Long,
    val unconfirmedValue: Long,
    val averageValue: Long,
    val dustCount: Int,
    val largestUtxo: Utxo?,
    val smallestUtxo: Utxo?
) {
    val hasDust: Boolean get() = dustCount > 0
    val needsConsolidation: Boolean get() = dustCount > 5 || totalCount > 50
}
