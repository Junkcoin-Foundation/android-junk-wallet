package junkwallet.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import junkwallet.domain.model.AddressType

@Singleton
class WalletStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Wallet Storage ──

    fun hasStoredWallet(): Boolean {
        return prefs.contains(KEY_ENCRYPTED_WIF)
    }

    /**
     * Save WIF encrypted with user's password using PBKDF2 + AES-GCM.
     */
    fun saveEncryptedWif(wif: String, password: String) {
        val salt = generateRandomBytes(16)
        val iv = generateRandomBytes(12)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(wif.toByteArray(Charsets.UTF_8))

        // Store: salt(16) + iv(12) + ciphertext( includes GCM tag)
        val combined = salt + iv + ciphertext
        val encoded = Base64.getEncoder().encodeToString(combined)
        prefs.edit().putString(KEY_ENCRYPTED_WIF, encoded).apply()
    }

    /**
     * Decrypt stored WIF using user's password.
     * Returns null if decryption fails (wrong password).
     */
    fun getDecryptedWif(password: String): String? {
        val encoded = prefs.getString(KEY_ENCRYPTED_WIF, null) ?: return null
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            val salt = combined.copyOfRange(0, 16)
            val iv = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)

            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            null // Wrong password or corrupted data
        }
    }

    /**
     * Check if the provided password matches the stored one.
     */
    fun verifyPassword(password: String): Boolean {
        return getDecryptedWif(password) != null
    }

    // ── Address Cache ──

    fun saveAddress(address: String) {
        prefs.edit().putString(KEY_ADDRESS, address).apply()
    }

    fun getAddress(): String? {
        return prefs.getString(KEY_ADDRESS, null)
    }

    // ── Network ──

    fun saveNetwork(network: String) {
        prefs.edit().putString(KEY_NETWORK, network).apply()
    }

    fun getNetwork(): String {
        return prefs.getString(KEY_NETWORK, NETWORK_MAINNET) ?: NETWORK_MAINNET
    }

    // ── Address Type ──

    fun saveDefaultAddressType(addressType: String) {
        prefs.edit().putString(KEY_DEFAULT_ADDRESS_TYPE, addressType).apply()
    }

    fun getDefaultAddressType(): AddressType {
        val saved = prefs.getString(KEY_DEFAULT_ADDRESS_TYPE, AddressType.P2PKH.name)
        return try {
            AddressType.valueOf(saved ?: AddressType.P2PKH.name)
        } catch (e: Exception) {
            AddressType.P2PKH
        }
    }

    // ── Session WIF (decrypted, in-memory only) ──

    @Volatile
    private var sessionWif: String? = null

    fun saveSessionWif(wif: String) {
        sessionWif = wif
    }

    fun getSessionWif(): String? = sessionWif

    fun clearSessionWif() {
        sessionWif = null
    }

    /**
     * Get WIF from session or decrypt with password.
     */
    fun getWif(password: String? = null): String? {
        // First try session
        sessionWif?.let { return it }

        // Then try decrypt
        if (password != null) {
            return getDecryptedWif(password)
        }

        return null
    }

    // ── Delete Wallet ──

    fun deleteWallet() {
        prefs.edit().clear().apply()
        sessionWif = null
    }

    // ── Crypto Helpers ──

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    companion object {
        private const val PREFS_FILE_NAME = "junk_wallet_secure"
        private const val KEY_ENCRYPTED_WIF = "encrypted_wif"
        private const val KEY_ADDRESS = "wallet_address"
        private const val KEY_NETWORK = "network_type"
        private const val KEY_DEFAULT_ADDRESS_TYPE = "default_address_type"

        const val NETWORK_MAINNET = "mainnet"
        const val NETWORK_TESTNET = "testnet"
    }
}
