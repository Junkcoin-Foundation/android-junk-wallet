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
import junkwallet.domain.model.NetworkType
import junkwallet.domain.model.WalletAccount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        prefs.edit()
            .putString(KEY_ENCRYPTED_WIF, encoded)
            // Remember which KDF cost produced this blob so a later decrypt
            // runs exactly one PBKDF2 instead of up to three.
            .putInt(KEY_WIF_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
    }

    /**
     * Decrypt stored WIF using user's password.
     * Returns null if decryption fails (wrong password).
     * Supports backward compatibility with old iteration counts (100K, 210K).
     */
    fun getDecryptedWif(password: String): String? {
        val encoded = prefs.getString(KEY_ENCRYPTED_WIF, null) ?: return null
        // Known iteration count (stored with the blob) → single KDF attempt.
        // Unknown (blob written before this metadata existed) → try the old
        // counts for backward compatibility.
        val iterationCounts = if (prefs.contains(KEY_WIF_ITERATIONS)) {
            listOf(prefs.getInt(KEY_WIF_ITERATIONS, PBKDF2_ITERATIONS))
        } else {
            listOf(PBKDF2_ITERATIONS, PBKDF2_ITERATIONS_V1, PBKDF2_ITERATIONS_V2)
        }
        for (iterations in iterationCounts) {
            val result = tryDecryptWif(password, encoded, iterations)
            if (result != null) {
                // If we decrypted with old iterations, re-encrypt with new iterations
                if (iterations != PBKDF2_ITERATIONS) {
                    saveEncryptedWif(result, password)
                }
                return result
            }
        }
        return null
    }

    private fun tryDecryptWif(password: String, encoded: String, iterations: Int): String? {
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            val salt = combined.copyOfRange(0, 16)
            val iv = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            val key = deriveKeyWithIterations(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)

            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            null
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

    /**
     * The active account's private key (WIF). Cleared on lock.
     */
    @Volatile
    private var sessionWif: String? = null

    /**
     * The wallet's ROOT private key (the one encrypted with the user password/PIN).
     * Used to encrypt per-account keys so they can be unlocked while the wallet is
     * unlocked, without plumbing the password through every ViewModel.
     * Cleared on lock.
     */
    @Volatile
    private var masterWif: String? = null

    /**
     * Set the active account key. If no root key is known yet (fresh unlock),
     * remember it as the root key as well.
     */
    fun saveSessionWif(wif: String) {
        sessionWif = wif
        if (masterWif == null) masterWif = wif
    }

    /**
     * Switch the active account key WITHOUT touching the root key.
     */
    fun setActiveAccountWif(wif: String) {
        sessionWif = wif
    }

    fun saveMasterWif(wif: String) {
        masterWif = wif
        if (sessionWif == null) sessionWif = wif
    }

    fun getMasterWif(): String? = masterWif

    fun getSessionWif(): String? = sessionWif

    fun clearSessionWif() {
        sessionWif = null
        masterWif = null
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

    // ── PIN Storage ──

    /**
     * Save PIN-encrypted WIF (alternative unlock method).
     */
    fun savePinEncryptedWif(wif: String, pin: String) {
        val salt = generateRandomBytes(16)
        val iv = generateRandomBytes(12)
        val key = deriveKey(pin, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(wif.toByteArray(Charsets.UTF_8))

        val combined = salt + iv + ciphertext
        val encoded = Base64.getEncoder().encodeToString(combined)
        prefs.edit()
            .putString(KEY_PIN_ENCRYPTED_WIF, encoded)
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
    }

    /**
     * Verify PIN by attempting to decrypt PIN-encrypted WIF.
     */
    fun verifyPin(pin: String): Boolean {
        return getDecryptedWifByPin(pin) != null
    }

    /**
     * Check if PIN is set.
     */
    fun hasPin(): Boolean {
        return prefs.contains(KEY_PIN_ENCRYPTED_WIF)
    }

    /**
     * Remove PIN.
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_ENCRYPTED_WIF)
            .remove(KEY_PIN_ITERATIONS)
            .apply()
    }

    /**
     * Decrypt WIF using PIN.
     * Supports backward compatibility with old iteration counts.
     */
    fun getDecryptedWifByPin(pin: String): String? {
        val encoded = prefs.getString(KEY_PIN_ENCRYPTED_WIF, null) ?: return null
        val iterationCounts = if (prefs.contains(KEY_PIN_ITERATIONS)) {
            listOf(prefs.getInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS))
        } else {
            listOf(PBKDF2_ITERATIONS, PBKDF2_ITERATIONS_V1, PBKDF2_ITERATIONS_V2)
        }
        for (iterations in iterationCounts) {
            val result = tryDecryptWifByPin(pin, encoded, iterations)
            if (result != null) {
                // If we decrypted with old iterations, re-encrypt with new iterations
                if (iterations != PBKDF2_ITERATIONS) {
                    savePinEncryptedWif(result, pin)
                }
                return result
            }
        }
        return null
    }

    private fun tryDecryptWifByPin(pin: String, encoded: String, iterations: Int): String? {
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            val salt = combined.copyOfRange(0, 16)
            val iv = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            val key = deriveKeyWithIterations(pin, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)

            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // ── Delete Wallet ──

    fun deleteWallet() {
        prefs.edit().clear().apply()
        sessionWif = null
        masterWif = null
    }

    // ── Multi-Account Storage ──

    private val json = Json { ignoreUnknownKeys = true }

    fun getAccounts(): List<WalletAccount> {
        val accountsJson = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<WalletAccount>>(accountsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<WalletAccount>) {
        val accountsJson = json.encodeToString(accounts)
        prefs.edit().putString(KEY_ACCOUNTS_JSON, accountsJson).apply()
    }

    fun getActiveAccountId(): String? {
        return prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)
    }

    fun setActiveAccountId(accountId: String) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, accountId).apply()
    }

    /**
     * Decrypt an account's private key. Requires the wallet to be unlocked
     * (root key in memory). Returns null when locked or when the account has
     * no stored key yet.
     */
    fun getAccountWif(accountId: String): String? {
        val root = masterWif ?: return null
        val key = "${KEY_ACCOUNT_ENCRYPTED_WIF_PREFIX}$accountId"
        val encoded = prefs.getString(key, null) ?: return null
        // Missing metadata = blob written by an older build (always 600K).
        val iterations = prefs.getInt(KEY_ACCOUNT_ITERATIONS_PREFIX + accountId, PBKDF2_ITERATIONS)
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            val salt = combined.copyOfRange(0, 16)
            val iv = combined.copyOfRange(16, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)

            val deriveKey = deriveKeyWithIterations(root, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey, GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            val wif = String(plaintext, Charsets.UTF_8)

            // One-time migration: account keys are protected by an already
            // derived 256-bit root secret, so they do not need the password
            // stretching cost. Re-encrypt cheaply so unlock() stops burning
            // seconds of main thread time on PBKDF2.
            if (iterations != ACCOUNT_KEY_ITERATIONS) {
                saveAccountEncryptedWif(accountId, wif)
            }
            wif
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Whether an encrypted key exists for this account (even if it cannot be
     * decrypted with the current root key).
     */
    fun hasAccountWif(accountId: String): Boolean =
        prefs.contains("${KEY_ACCOUNT_ENCRYPTED_WIF_PREFIX}$accountId")

    /**
     * Encrypt and persist an account's private key (rooted at the wallet's own key).
     */
    fun saveAccountEncryptedWif(accountId: String, wif: String): Boolean {
        val root = masterWif ?: return false
        val salt = generateRandomBytes(16)
        val iv = generateRandomBytes(12)
        val key = deriveKeyWithIterations(root, salt, ACCOUNT_KEY_ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(wif.toByteArray(Charsets.UTF_8))

        val combined = salt + iv + ciphertext
        val encoded = Base64.getEncoder().encodeToString(combined)
        val storageKey = "${KEY_ACCOUNT_ENCRYPTED_WIF_PREFIX}$accountId"
        prefs.edit()
            .putString(storageKey, encoded)
            .putInt(KEY_ACCOUNT_ITERATIONS_PREFIX + accountId, ACCOUNT_KEY_ITERATIONS)
            .apply()
        return true
    }

    fun deleteAccountStorage(accountId: String) {
        val storageKey = "${KEY_ACCOUNT_ENCRYPTED_WIF_PREFIX}$accountId"
        prefs.edit().remove(storageKey).apply()
    }

    fun migrateSingleWalletToAccount() {
        if (hasStoredWallet() && getAccounts().isEmpty()) {
            val wif = masterWif ?: sessionWif
            if (wif != null) {
                val network = when (getNetwork()) {
                    "testnet" -> NetworkType.TESTNET
                    else -> NetworkType.MAINNET
                }
                val account = WalletAccount(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "Account 1",
                    network = network,
                    defaultAddressType = getDefaultAddressType()
                )
                saveAccounts(listOf(account))
                setActiveAccountId(account.id)
                // The root key IS this account's key.
                saveAccountEncryptedWif(account.id, wif)
            }
        }
    }

    // ── Crypto Helpers ──

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        return deriveKeyWithIterations(password, salt, PBKDF2_ITERATIONS)
    }

    private fun deriveKeyWithIterations(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
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
        private const val KEY_PIN_ENCRYPTED_WIF = "pin_encrypted_wif"
        private const val KEY_WIF_ITERATIONS = "encrypted_wif_iterations"
        private const val KEY_PIN_ITERATIONS = "pin_encrypted_wif_iterations"
        private const val KEY_ADDRESS = "wallet_address"
        private const val KEY_NETWORK = "network_type"
        private const val KEY_DEFAULT_ADDRESS_TYPE = "default_address_type"
        private const val PBKDF2_ITERATIONS = 600_000      // Current (OWASP 2023)
        private const val PBKDF2_ITERATIONS_V1 = 100_000   // Old v1 (pre-PIN)
        private const val PBKDF2_ITERATIONS_V2 = 210_000   // Old v2 (post-PIN, pre-600K)
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_ACCOUNT_ENCRYPTED_WIF_PREFIX = "account_"
        private const val KEY_ACCOUNT_ITERATIONS_PREFIX = "account_iterations_"

        // Account keys are encrypted with the root key itself (a derived
        // 256-bit secret), not with a user password, so password stretching
        // is pointless: keep a cheap KDF so unlocking stays instant.
        private const val ACCOUNT_KEY_ITERATIONS = 10_000

        const val NETWORK_MAINNET = "mainnet"
        const val NETWORK_TESTNET = "testnet"
    }
}
