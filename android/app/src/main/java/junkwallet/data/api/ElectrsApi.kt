package junkwallet.data.api

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import junkwallet.data.model.AddressInfo
import junkwallet.data.model.Transaction
import junkwallet.data.model.Utxo

interface ElectrsApi {

    // ── Block Info ──

    @GET("blocks/tip/height")
    suspend fun getBlockHeight(): String

    @GET("blocks/tip/hash")
    suspend fun getBlockHash(): String

    @GET("block-height/{height}")
    suspend fun getBlockHashByHeight(@Path("height") height: Int): String

    // ── Address Info ──

    @GET("address/{address}")
    suspend fun getAddressInfo(@Path("address") address: String): AddressInfo

    /**
     * Get first page of address transactions.
     * Returns up to 50 mempool txs + first 25 confirmed txs.
     */
    @GET("address/{address}/txs")
    suspend fun getAddressTxs(
        @Path("address") address: String
    ): List<Transaction>

    /**
     * Paginate confirmed address transactions.
     * Returns 25 confirmed txs per page.
     * Use last_seen_txid from previous page to fetch next page.
     */
    @GET("address/{address}/txs/chain/{last_seen_txid}")
    suspend fun getAddressTxsChain(
        @Path("address") address: String,
        @Path("last_seen_txid") lastSeenTxid: String
    ): List<Transaction>

    @GET("address/{address}/utxo")
    suspend fun getAddressUtxos(
        @Path("address") address: String
    ): List<Utxo>

    // ── Transactions ──

    @GET("tx/{txid}")
    suspend fun getTransaction(@Path("txid") txid: String): Transaction

    @GET("tx/{txid}/hex")
    suspend fun getTransactionHex(@Path("txid") txid: String): String

    // ── Fees ──

    @GET("fee-estimates")
    suspend fun getFeeEstimates(): Map<String, Double>

    // ── Broadcast ──

    @POST("tx")
    @Headers("Content-Type: text/plain")
    suspend fun broadcastTransaction(@retrofit2.http.Body rawTx: okhttp3.RequestBody): retrofit2.Response<String>
}
