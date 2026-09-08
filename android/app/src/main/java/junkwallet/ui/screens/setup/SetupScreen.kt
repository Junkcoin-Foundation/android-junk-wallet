package junkwallet.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import junkwallet.ui.theme.Background
import junkwallet.ui.theme.PrimaryCyan
import junkwallet.ui.theme.TextHighEmphasis
import junkwallet.ui.theme.TextMuted

@Composable
fun SetupScreen(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A self-custodial wallet for Junkcoin",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onCreateWallet,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Create New Wallet",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onImportWallet,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Import Existing Wallet",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
