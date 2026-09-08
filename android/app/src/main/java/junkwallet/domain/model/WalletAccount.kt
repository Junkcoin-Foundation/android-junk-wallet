package junkwallet.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a wallet account with its own private key and settings.
 */
@Serializable
data class WalletAccount(
    val id: String,
    val name: String,
    val network: NetworkType,
    val defaultAddressType: AddressType = AddressType.P2PKH,
    val createdAt: Long = System.currentTimeMillis()
)
