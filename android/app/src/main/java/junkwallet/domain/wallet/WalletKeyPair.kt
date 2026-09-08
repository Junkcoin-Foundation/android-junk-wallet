package junkwallet.domain.wallet

/**
 * Represents a wallet keypair with WIF and address.
 */
data class WalletKeyPair(
    val wif: String,
    val address: String,
    val publicKey: String
)
