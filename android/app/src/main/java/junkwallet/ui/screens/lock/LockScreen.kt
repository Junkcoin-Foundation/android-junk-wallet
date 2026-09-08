package junkwallet.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.ErrorCrimson
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.SurfaceContainerHigh
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted

@Composable
fun LockScreen(
    onPasswordVerified: (password: String) -> Unit,
    onBiometricRequested: () -> Unit,
    hasStoredWallet: Boolean = true,
    error: String? = null,
    biometricAvailable: Boolean = false
) {
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val displayError = error ?: localError

    // Auto-trigger biometric if available and no error
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && error == null) {
            onBiometricRequested()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = "JKC",
            style = MaterialTheme.typography.displayLarge,
            color = PrimaryCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Junkcoin Wallet",
            style = MaterialTheme.typography.headlineLarge,
            color = TextHighEmphasis
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Enter Password",
            style = MaterialTheme.typography.titleMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; localError = null },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = SurfaceContainerHigh
            ),
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            isError = displayError != null,
            placeholder = { Text("Enter your password", color = TextMuted) }
        )

        if (displayError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = displayError,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorCrimson
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (password.isBlank()) {
                    localError = "Please enter your password"
                } else {
                    onPasswordVerified(password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Unlock",
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (hasStoredWallet && biometricAvailable) {
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBiometricRequested,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = "Fingerprint",
                    modifier = Modifier.size(24.dp),
                    tint = PrimaryCyan
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Use Fingerprint",
                    color = TextHighEmphasis
                )
            }
        }
    }
}
