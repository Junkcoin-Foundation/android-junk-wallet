package junkwallet.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "junk_wallet_settings")

@Singleton
class SettingsStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // ── Network ──
    val networkType: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_NETWORK] ?: "mainnet"
    }

    suspend fun setNetworkType(network: String) {
        dataStore.edit { prefs ->
            prefs[KEY_NETWORK] = network
        }
    }

    // ── Electrs endpoint ──
    val electrsEndpoint: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_ELECTRS_ENDPOINT] ?: ""
    }

    suspend fun setElectrsEndpoint(endpoint: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ELECTRS_ENDPOINT] = endpoint
        }
    }

    // ── Block explorer URL ──
    val explorerUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_EXPLORER_URL] ?: ""
    }

    suspend fun setExplorerUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_EXPLORER_URL] = url
        }
    }

    // ── Biometric ──
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC] ?: false
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC] = enabled
        }
    }

    // ── Auto-lock timeout ──
    val autoLockMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_LOCK] ?: 3
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_LOCK] = minutes
        }
    }

    // ── Stay signed in ──
    val staySignedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_STAY_SIGNED_IN] ?: false
    }

    suspend fun setStaySignedIn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_STAY_SIGNED_IN] = enabled
        }
    }

    // ── Default address type ──
    val defaultAddressType: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_ADDRESS_TYPE] ?: "legacy"
    }

    suspend fun setAddressType(type: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ADDRESS_TYPE] = type
        }
    }

    companion object {
        private val KEY_NETWORK = stringPreferencesKey("network_type")
        private val KEY_ELECTRS_ENDPOINT = stringPreferencesKey("electrs_endpoint")
        private val KEY_EXPLORER_URL = stringPreferencesKey("explorer_url")
        private val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        private val KEY_AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        private val KEY_STAY_SIGNED_IN = booleanPreferencesKey("stay_signed_in")
        private val KEY_ADDRESS_TYPE = stringPreferencesKey("address_type")
    }
}
