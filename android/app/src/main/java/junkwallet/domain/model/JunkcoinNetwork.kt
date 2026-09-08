package junkwallet.domain.model

import kotlinx.serialization.Serializable

/**
 * Address type enumeration for Junkcoin.
 * P2PKH: Legacy address (starts with '7' mainnet, 'm'/'n' testnet)
 * P2SH-P2WPKH: SegWit wrapped in P2SH (starts with '3' mainnet, '2' testnet)
 * P2WPKH: Native SegWit (bech32, starts with 'jc1...' mainnet, 'tjc1...' testnet)
 * P2TR: Taproot (bech32m, starts with 'jc1p...' mainnet, 'tjc1p...' testnet)
 */
enum class AddressType {
    P2PKH,          // Legacy
    P2SH_P2WPKH,    // Wrapped SegWit
    P2WPKH,         // Native SegWit (v0)
    P2TR            // Taproot (v1)
}

@Serializable
data class JunkcoinParams(
    val name: String,
    val pubKeyHash: Int,
    val scriptHash: Int,
    val wif: Int,
    val bech32Hrp: String?,
    val messagePrefix: String,
    val bip32Public: Int,
    val bip32Private: Int,
    val txVersion: Int,
    val p2pPort: Int,
    val rpcPort: Int,
    val dnsSeeds: List<String>,
    val electrsUrl: String,
    val explorerUrl: String,
    val coinbaseMaturity: Int = 70,
    val segwitActivatedAt: Long? = null,     // Block height when SegWit activated
    val taprootActivatedAt: Long? = null     // Block height when Taproot activated
) {
    val addressPrefix: String
        get() = when (pubKeyHash) {
            0x10 -> "7"   // mainnet
            0x6F -> "m"   // testnet
            else -> "?"
        }

    val wifPrefix: String
        get() = when (wif) {
            0x90 -> "N"   // mainnet
            0xEF -> "c"   // testnet
            else -> "?"
        }

    /**
     * Get the bech32 human-readable part for the network.
     */
    val bech32: String
        get() = bech32Hrp ?: when (pubKeyHash) {
            0x47 -> "jc"    // mainnet
            0x6F -> "tjc"   // testnet
            else -> "jc"
        }

    /**
     * Get the P2SH prefix for wrapped SegWit.
     */
    val p2shPrefix: String
        get() = when (scriptHash) {
            0x05 -> "3"    // mainnet
            0xC4 -> "2"    // testnet
            else -> "3"
        }

    /**
     * Check if SegWit is supported on this network.
     */
    val isSegwitSupported: Boolean
        get() = segwitActivatedAt != null

    /**
     * Check if Taproot is supported on this network.
     */
    val isTaprootSupported: Boolean
        get() = taprootActivatedAt != null

    /**
     * Get all supported address types for this network.
     */
    val supportedAddressTypes: List<AddressType>
        get() {
            val types = mutableListOf(AddressType.P2PKH)
            if (isSegwitSupported) {
                types.add(AddressType.P2SH_P2WPKH)
                types.add(AddressType.P2WPKH)
            }
            if (isTaprootSupported) {
                types.add(AddressType.P2TR)
            }
            return types
        }
}

object JunkcoinNetwork {
    val MAINNET = JunkcoinParams(
        name = "mainnet",
        pubKeyHash = 0x10,  // 16 decimal — produces addresses starting with '7'
        scriptHash = 0x05,
        wif = 0x90,
        bech32Hrp = "jc",
        messagePrefix = "\u0019Junkcoin Signed Message:\n",
        bip32Public = 0x0488B21E,
        bip32Private = 0x0488ADE4,
        txVersion = 1,
        p2pPort = 9771,
        rpcPort = 9772,
        dnsSeeds = listOf(
            "mainnet.junk-coin.com",
            "junk-seed.s3na.xyz",
            "jkc-seed.junkiewally.xyz"
        ),
        electrsUrl = "https://junk-api.s3na.xyz",
        explorerUrl = "https://explorer.junk-coin.com",
        segwitActivatedAt = 140000,   // SegWit activated at block 140,000
        taprootActivatedAt = 160000    // Taproot activated at block 160,000
    )

    val TESTNET = JunkcoinParams(
        name = "testnet",
        pubKeyHash = 0x6F,
        scriptHash = 0xC4,
        wif = 0xEF,
        bech32Hrp = "tjc",
        messagePrefix = "\u0019Junkcoin Signed Message:\n",
        bip32Public = 0x02FACAFD,
        bip32Private = 0x02FAC398,
        txVersion = 1,
        p2pPort = 19771,
        rpcPort = 19772,
        dnsSeeds = listOf(
            "testnet.junk-coin.com",
            "junk-testnet.s3na.xyz"
        ),
        electrsUrl = "https://jkc-testnet-api.s3na.xyz",
        explorerUrl = "https://explorer.junk-coin.com/testnet",
        segwitActivatedAt = 140000,
        taprootActivatedAt = 160000
    )
}
