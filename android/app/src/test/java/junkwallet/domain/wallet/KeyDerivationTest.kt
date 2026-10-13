package junkwallet.domain.wallet

import junkwallet.domain.model.JunkcoinNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyDerivationTest {

    private val crypto = JunkcoinCrypto(bech32Encoder = Bech32Encoder())

    @Test
    fun testWifToAddressDerivation() {
        // The test WIF from user report
        val wif = "NRkC88epH4rEpy3NrZpRw1gMwcw9YhnTgjxgDZ2hARg7kKxVYFEa"

        val keyPair = crypto.getKeyPairFromWif(wif)
        val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
        val p2pkhAddress = crypto.createP2PKHAddress(compressedPubKey, JunkcoinNetwork.MAINNET)

        // Must match 7battz2ceyCMyq8NUY6f2wQj4KYMAQTqbp (which multichain-android and 3rd-party wallets produce)
        assertEquals("7battz2ceyCMyq8NUY6f2wQj4KYMAQTqbp", p2pkhAddress)

        // Confirms that 7mzn4tBpeWwc7b3XkGcPGizgZPwUpG9cfP is NOT derived from this WIF (it belonged to Account 2)
        assertNotEquals("7mzn4tBpeWwc7b3XkGcPGizgZPwUpG9cfP", p2pkhAddress)
    }

    @Test
    fun testGeneratedWalletConsistency() {
        val wallet = crypto.generateWallet(JunkcoinNetwork.MAINNET)

        // Deriving address from the exported WIF must match the generated wallet address
        val keyPair = crypto.getKeyPairFromWif(wallet.wif)
        val compressedPubKey = crypto.getCompressedPublicKey(keyPair.public)
        val derivedAddress = crypto.createP2PKHAddress(compressedPubKey, JunkcoinNetwork.MAINNET)

        assertEquals(wallet.address, derivedAddress)
    }
}
