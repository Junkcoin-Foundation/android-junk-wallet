package junkwallet.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object CreateWallet : Screen("create_wallet")
    data object ImportWallet : Screen("import_wallet")
    data object Backup : Screen("backup/{wif}") {
        fun createRoute(wif: String) = "backup/$wif"
    }
    data object Lock : Screen("lock")
    data object Dashboard : Screen("dashboard")
    data object Send : Screen("send")
    data object ConfirmSend : Screen("confirm_send")
    data object Receive : Screen("receive")
    data object History : Screen("history")
    data object TxDetail : Screen("tx_detail/{txid}") {
        fun createRoute(txid: String) = "tx_detail/$txid"
    }
    data object Settings : Screen("settings")
    data object KeyVault : Screen("key_vault")
    data object NetworkSettings : Screen("network_settings")
    data object QrScanner : Screen("qr_scanner")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Wallet", Icons.Filled.AccountBalanceWallet),
    BottomNavItem(Screen.Send, "Send", Icons.Filled.Send),
    BottomNavItem(Screen.History, "History", Icons.Filled.History),
    BottomNavItem(Screen.Settings, "Settings", Icons.Filled.Settings)
)
