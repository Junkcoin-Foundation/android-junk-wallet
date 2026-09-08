package junkwallet.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoinGeckoPrice(
    @SerialName("junkcoin") val junkcoin: JunkcoinUsd? = null
) {
    @Serializable
    data class JunkcoinUsd(
        val usd: Double = 0.0
    )
}
