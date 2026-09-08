package junkwallet.ui.screens.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SurfaceContainer
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TertiaryAmber
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWalletScreen(
    onWalletCreated: () -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    var showWif by remember { mutableStateOf(false) }
    var backupConfirmed by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val wallet = viewModel.generatedWallet

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Create Wallet") },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Text(
                text = "Step 1: Backup Your Private Key",
                style = MaterialTheme.typography.headlineMedium,
                color = TextHighEmphasis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Write down your private key and store it somewhere safe. This is the ONLY way to recover your wallet if you lose your password.",
                style = MaterialTheme.typography.bodyMedium,
                color = TertiaryAmber
            )

            Spacer(modifier = Modifier.height(16.dp))

            // WIF display card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (showWif) (wallet?.wif ?: "") else "••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextHighEmphasis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showWif = !showWif },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceContainerHigh
                        ),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = if (showWif) "Hide Key" else "Reveal Key",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "⚠ Never share this key with anyone. Never enter it on any website.",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorCrimson
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Derived Address: ${wallet?.address}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Backup confirmation
            Button(
                onClick = { backupConfirmed = !backupConfirmed },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (backupConfirmed) PrimaryCyan
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (backupConfirmed) "✓ Key Saved" else "I have saved my private key",
                    color = if (backupConfirmed) MaterialTheme.colorScheme.onPrimary else TextHighEmphasis
                )
            }

            if (backupConfirmed) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Step 2: Set Password",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextHighEmphasis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This password encrypts your wallet on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.updatePassword(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = SurfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.confirmPassword,
                    onValueChange = { viewModel.updateConfirmPassword(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = SurfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = viewModel.confirmPassword.isNotEmpty() && !viewModel.isPasswordValid,
                    supportingText = if (viewModel.confirmPassword.isNotEmpty() && !viewModel.isPasswordValid) {
                        { Text("Passwords must match and be at least 6 characters", color = ErrorCrimson) }
                    } else null
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (viewModel.storeWallet()) {
                            onWalletCreated()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.isPasswordValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Create Wallet",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
