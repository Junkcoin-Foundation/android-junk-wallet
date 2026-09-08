package junkwallet.data.repository

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import junkwallet.data.api.ElectrsApi
import junkwallet.data.model.AddressInfo
import junkwallet.data.model.Transaction
import junkwallet.data.model.Utxo
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.model.FeeEstimates
import junkwallet.domain.model.NetworkType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockchainRepository @Inject constructor(
    private val mainnetApi: ElectrsApi,
    private val testnetApi: ElectrsApi,
    private val storage: WalletStorage
) {
    companion object {
        private const val CHAIN_TXS_PER_PAGE = 25
    }

    private val activeApi: ElectrsApi
        get() {
            val network = storage.getNetwork()
            Log.d("BlockchainRepo", "activeApi: network=$network → ${if (network == WalletStorage.NETWORK_TESTNET) "TESTNET" else "MAINNET"}")
            return when (network) {
                WalletStorage.NETWORK_TESTNET -> testnetApi
                else -> mainnetApi
            }
        }

    fun getWalletAddress(): String {
        return storage.getAddress() ?: ""
    }

    fun getWif(): String? {
        return storage.getSessionWif()
    }

    suspend fun getBlockHeight(): Int {
        return try {
            activeApi.getBlockHeight().trim().toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getAddressInfo(address: String): AddressInfo {
        return try {
            activeApi.getAddressInfo(address)
        } catch (e: Exception) {
            AddressInfo()
        }
    }

    suspend fun getBalance(address: String): Long {
        return try {
            val info = activeApi.getAddressInfo(address)
            info.confirmedBalance
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getBalancePair(address: String): Pair<Long, Long> {
        return try {
            val info = activeApi.getAddressInfo(address)
            Log.d("BlockchainRepo", "Balance for $address: confirmed=${info.confirmedBalance} unconfirmed=${info.unconfirmedBalance}")
            Pair(info.confirmedBalance, info.unconfirmedBalance)
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "getBalancePair failed: ${e.message}")
            Pair(0L, 0L)
        }
    }

    /**
     * Fetch all transactions for an address using Electrs pagination.
     *
     * Electrs REST API:
     * - GET /address/{address}/txs  →  up to 50 mempool + first 25 confirmed
     * - GET /address/{address}/txs/chain/{last_seen_txid}  →  next 25 confirmed
     * - Stop when page returns < 25 txs
     */
    suspend fun getTransactions(address: String): List<Transaction> {
        return try {
            val allTxs = mutableListOf<Transaction>()

            // First page: /address/{address}/txs
            val firstPage = activeApi.getAddressTxs(address)
            allTxs.addAll(firstPage)
            Log.d("BlockchainRepo", "Txs first page: ${firstPage.size} txs for $address")

            // Check if we got a full page of confirmed txs
            // If we got exactly 25 confirmed txs, there may be more
            if (firstPage.size >= CHAIN_TXS_PER_PAGE) {
                // Find last confirmed txid for cursor
                val lastConfirmed = firstPage.lastOrNull()
                if (lastConfirmed != null && lastConfirmed.status.confirmed) {
                    var lastSeenTxid = lastConfirmed.txid

                    // Paginate through remaining confirmed txs
                    while (true) {
                        val page = activeApi.getAddressTxsChain(address, lastSeenTxid)
                        if (page.isEmpty()) break

                        allTxs.addAll(page)

                        if (page.size < CHAIN_TXS_PER_PAGE) break

                        lastSeenTxid = page.last().txid
                    }
                }
            }

            allTxs
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "getTransactions failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUtxos(address: String): List<Utxo> {
        return try {
            activeApi.getAddressUtxos(address)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTransactionHex(txid: String): String {
        return try {
            activeApi.getTransactionHex(txid)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun getFeeEstimates(): FeeEstimates {
        return try {
            val raw = activeApi.getFeeEstimates()
            FeeEstimates(
                blocks1 = raw["1"] ?: 0.0,
                blocks3 = raw["3"] ?: 0.0,
                blocks6 = raw["6"] ?: 0.0,
                blocks12 = raw["12"] ?: 0.0,
                blocks24 = raw["24"] ?: 0.0,
                blocks144 = raw["144"] ?: 0.0
            )
        } catch (e: Exception) {
            FeeEstimates()
        }
    }

    suspend fun broadcastTransaction(rawTxHex: String): Boolean {
        return try {
            val body = rawTxHex.toRequestBody("text/plain".toMediaType())
            val response = activeApi.broadcastTransaction(body)
            if (response.isSuccessful) {
                true
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("BlockchainRepo", "Broadcast failed: ${response.code()} $errorBody")
                false
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Broadcast exception: ${e.message}", e)
            false
        }
    }
}
