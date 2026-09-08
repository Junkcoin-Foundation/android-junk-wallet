package junkwallet.ui.screens.settings

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import junkwallet.data.storage.WalletStorage
import junkwallet.domain.wallet.JunkcoinCrypto
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.theme.TertiaryAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultScreen(
    onBack: () -> Unit,
    storage: WalletStorage,
    crypto: JunkcoinCrypto
) {
    var password by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var revealedKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerified by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Key Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = TextHighEmphasis
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .imePadding()
        ) {
            // ── Security Header ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Private Key Vault",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextHighEmphasis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your private key is encrypted with AES-256-GCM and stored in Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password Verification ──
            if (!isVerified) {
                Text(
                    text = "Enter Password to View Key",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = SurfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(it, color = ErrorCrimson) }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val wif = storage.getDecryptedWif(password)
                        if (wif != null) {
                            revealedKey = wif
                            isVerified = true
                            error = null
                        } else {
                            error = "Incorrect password"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Verify Password", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                // ── Key Display ──
                Text(
                    text = "Your Private Key (WIF)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (showKey) (revealedKey ?: "") else "••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextHighEmphasis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showKey) "Hide" else "Show",
                                    tint = PrimaryCyan
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setText(AnnotatedString(revealedKey ?: ""))
                                Toast.makeText(context, "Private key copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = PrimaryCyan
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠ Never share this key. Never enter it on any website. Anyone with this key can steal your funds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorCrimson
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Derived Address ──
                Text(
                    text = "Derived Address",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = storage.getAddress() ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextHighEmphasis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Actions ──
                OutlinedButton(
                    onClick = {
                        showKey = false
                        isVerified = false
                        revealedKey = null
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Lock Key Vault")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setText(AnnotatedString(storage.getAddress() ?: ""))
                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Copy Address")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Danger zone
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ErrorCrimson.copy(alpha = 0.12f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorCrimson
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                storage.deleteWallet()
                                // TODO: navigate to setup
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorCrimson
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Delete Wallet", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }
        }
    }
}
