package junkwallet.domain.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable

@Serializable
data class WalletState(
    val hasStoredWallet: Boolean = false,
    val isLocked: Boolean = true,
    val address: String = "",
    val allAddresses: Map<String, String> = emptyMap(),
    val confirmedBalance: Long = 0,
    val unconfirmedBalance: Long = 0,
    val transactions: List<TransactionInfo> = emptyList(),
    val utxos: List<UtxoInfo> = emptyList(),
    val blockHeight: Int = 0,
    val feeEstimates: FeeEstimates = FeeEstimates(),
    val network: NetworkType = NetworkType.MAINNET,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastSynced: Long = 0
) {
    val totalBalance: Long get() = confirmedBalance + unconfirmedBalance
    val hasUnconfirmed: Boolean get() = unconfirmedBalance != 0L
    val isOnline: Boolean get() = error == null
    val lastSyncedText: String
        get() {
            if (lastSynced == 0L) return "Never"
            val diff = System.currentTimeMillis() - lastSynced
            return when {
                diff < 60_000 -> "Just now"
                diff < 3_600_000 -> "${diff / 60_000}m ago"
                diff < 86_400_000 -> "${diff / 3_600_000}h ago"
                else -> "${diff / 86_400_000}d ago"
            }
        }
}

enum class NetworkType {
    MAINNET, TESTNET
}

@Serializable
data class TransactionInfo(
    val txid: String,
    val confirmed: Boolean,
    val blockHeight: Int? = null,
    val blockTime: Long? = null,
    val fee: Long? = null,
    val sent: Long = 0,
    val received: Long = 0,
    val isSent: Boolean = false,
    val mempoolTime: Long? = null
) : Parcelable {
    val netAmount: Long get() = if (isSent) sent else received
    val isPending: Boolean get() = !confirmed

    constructor(parcel: Parcel) : this(
        txid = parcel.readString() ?: "",
        confirmed = parcel.readInt() == 1,
        blockHeight = parcel.readInt().let { if (it == -1) null else it },
        blockTime = parcel.readLong().let { if (it == -1L) null else it },
        fee = parcel.readLong().let { if (it == -1L) null else it },
        sent = parcel.readLong(),
        received = parcel.readLong(),
        isSent = parcel.readInt() == 1,
        mempoolTime = parcel.readLong().let { if (it == -1L) null else it }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(txid)
        parcel.writeInt(if (confirmed) 1 else 0)
        parcel.writeInt(blockHeight ?: -1)
        parcel.writeLong(blockTime ?: -1L)
        parcel.writeLong(fee ?: -1L)
        parcel.writeLong(sent)
        parcel.writeLong(received)
        parcel.writeInt(if (isSent) 1 else 0)
        parcel.writeLong(mempoolTime ?: -1L)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TransactionInfo> {
        override fun createFromParcel(parcel: Parcel) = TransactionInfo(parcel)
        override fun newArray(size: Int) = arrayOfNulls<TransactionInfo>(size)
    }
}

@Serializable
data class UtxoInfo(
    val txid: String,
    val vout: Int,
    val value: Long,
    val confirmed: Boolean,
    val blockHeight: Int? = null
)

@Serializable
data class FeeEstimates(
    val blocks1: Double = 0.0,
    val blocks3: Double = 0.0,
    val blocks6: Double = 0.0,
    val blocks12: Double = 0.0,
    val blocks24: Double = 0.0,
    val blocks144: Double = 0.0
) {
    val slowRate: Double get() = blocks12
    val standardRate: Double get() = blocks3
    val priorityRate: Double get() = blocks1
}

@Serializable
data class FiatPrice(
    val usd: Double = 0.0,
    val lastUpdated: Long = 0
)
