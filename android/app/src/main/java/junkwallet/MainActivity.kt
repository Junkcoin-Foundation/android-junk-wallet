package junkwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import junkwallet.data.storage.SettingsStorage
import junkwallet.data.storage.WalletStorage
import junkwallet.ui.JunkWalletNavHost
import junkwallet.ui.theme.JunkWalletTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var walletStorage: WalletStorage

    @Inject
    lateinit var settingsStorage: SettingsStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JunkWalletTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JunkWalletNavHost(
                        walletStorage = walletStorage,
                        settingsStorage = settingsStorage
                    )
                }
            }
        }
    }
}
