package junkwallet.domain.wallet

import java.math.BigInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.math.ec.ECPoint

/**
 * MWEB account keys (scan + spend) for an account.
 *
 * mwebd defines an MWEB account as a pair of ECDSA keys derived from a BIP32
 * root as the hardened `0'` / `1'` children of an account branch, e.g.
 * `m/1000'/<coin>'/<account>'/0'` (scan) and `.../1'` (spend).
 *
 * This wallet stores raw WIF keys instead of a BIP39 mnemonic, so the BIP32
 * root is built by treating the account's 32-byte private key as a BIP32 seed
 * (standard `HMAC-SHA512("Bitcoin seed", seed)` master key generation). The
 * derivation is deterministic per account and never leaves the device.
 */
data class MwebKeychain(
    val scanSecret: ByteArray,
    val spendSecret: ByteArray
) {
    /** 33-byte compressed SEC1 public key of the spend secret. */
    val spendPub: ByteArray by lazy { compressedPublicKey(spendSecret) }

    override fun equals(other: Any?): Boolean =
        other is MwebKeychain &&
            scanSecret.contentEquals(other.scanSecret) &&
            spendSecret.contentEquals(other.spendSecret)

    override fun hashCode(): Int =
        scanSecret.contentHashCode() * 31 + spendSecret.contentHashCode()

    companion object {
        fun fromAccountPrivateKey(privKey: ByteArray): MwebKeychain? {
            val root = bip32MasterFromSeed(privKey) ?: return null
            val scan = derivePath(root, MWEB_SCAN_PATH) ?: return null
            val spend = derivePath(root, MWEB_SPEND_PATH) ?: return null
            return MwebKeychain(scanSecret = scan, spendSecret = spend)
        }

        fun fromWif(wif: String): MwebKeychain? = try {
            val crypto = junkwallet.domain.wallet.JunkcoinCrypto(
                bech32Encoder = junkwallet.domain.wallet.Bech32Encoder()
            )
            val keyPair = crypto.getKeyPairFromWif(wif)
            fromAccountPrivateKey(crypto.getPrivateKeyBytes(keyPair.private))
        } catch (_: Exception) {
            null
        }

        // m/1000'/0'/0'/0'  (purpose 1000' = MWEB, per mwebd README)
        val MWEB_SCAN_PATH = listOf(1000u, 0u, 0u, 0u)
        // m/1000'/0'/0'/1'
        val MWEB_SPEND_PATH = listOf(1000u, 0u, 0u, 1u)

        private const val SEED_KEY = "Bitcoin seed"
        private const val HARDENED = 0x80000000u

        private val CURVE_N: BigInteger =
            ECNamedCurveTable.getParameterSpec("secp256k1").n

        internal data class Xprv(val key: BigInteger, val chainCode: ByteArray)

        /** BIP32 master key generation from a raw seed. */
        private fun bip32MasterFromSeed(seed: ByteArray): Xprv? {
            val i = hmacSha512(SEED_KEY.toByteArray(Charsets.US_ASCII), seed)
            val il = BigInteger(1, i.copyOfRange(0, 32))
            val ir = i.copyOfRange(32, 64)
            if (il <= BigInteger.ZERO || il >= CURVE_N) return null
            return Xprv(il, ir)
        }

        /** Derive a fully hardened path such as m/1000'/0'/0'/0'. */
        private fun derivePath(root: Xprv, path: List<UInt>): ByteArray? {
            var current = root
            for (index in path) {
                val childIndex = if (index >= HARDENED) index else index + HARDENED
                current = ckdPrivHardened(current, childIndex) ?: return null
            }
            return current.key.unsignedBytes(32)
        }

        private fun ckdPrivHardened(parent: Xprv, index: UInt): Xprv? {
            val data = ByteArray(37)
            data[0] = 0
            parent.key.unsignedBytes(32).copyInto(data, 1)
            data[33] = ((index shr 24) and 0xFFu).toByte()
            data[34] = ((index shr 16) and 0xFFu).toByte()
            data[35] = ((index shr 8) and 0xFFu).toByte()
            data[36] = (index and 0xFFu).toByte()

            val i = hmacSha512(parent.chainCode, data)
            val il = BigInteger(1, i.copyOfRange(0, 32))
            val ir = i.copyOfRange(32, 64)
            if (il >= CURVE_N) return null
            val child = il.add(parent.key).mod(CURVE_N)
            if (child == BigInteger.ZERO) return null
            return Xprv(child, ir)
        }

        private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA512")
            mac.init(SecretKeySpec(key, "HmacSHA512"))
            return mac.doFinal(data)
        }

        private fun compressedPublicKey(privKey: ByteArray): ByteArray {
            val ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
            val point: ECPoint = ecSpec.g.multiply(BigInteger(1, privKey)).normalize()
            return point.getEncoded(true)
        }

        private fun BigInteger.unsignedBytes(size: Int): ByteArray {
            val raw = toByteArray()
            return when {
                raw.size == size -> raw
                raw.size > size -> raw.copyOfRange(raw.size - size, raw.size)
                else -> ByteArray(size - raw.size) + raw
            }
        }
    }
}
