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
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted
import junkwallet.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    onWalletImported: () -> Unit,
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    var wifInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val isFormValid = wifInput.isNotBlank() &&
            password.length >= 6 &&
            password == confirmPassword

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Import Wallet") },
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
                text = "Private Key (WIF)",
                style = MaterialTheme.typography.headlineMedium,
                color = TextHighEmphasis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your existing WIF private key to import your wallet.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = wifInput,
                onValueChange = { wifInput = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WIF Private Key") },
                placeholder = { Text("Enter WIF key...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = SurfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.medium,
                singleLine = false,
                minLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Set Password") },
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
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm Password") },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = SurfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                supportingText = if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                    { Text("Passwords do not match", color = ErrorCrimson) }
                } else null
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorCrimson
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val result = viewModel.importWallet(wifInput.trim(), password)
                    result.onSuccess { onWalletImported() }
                    result.onFailure { error = it.message }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryCyan
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Import Wallet",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
