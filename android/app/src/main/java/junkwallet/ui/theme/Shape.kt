package junkwallet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ── Stitch Shapes: Obsidian Cyber-UTXO ──

val JunkcoinShapes = Shapes(
    // Extra small — chips, badges, pills
    extraSmall = RoundedCornerShape(4.dp),

    // Small — input fields, nested elements
    small = RoundedCornerShape(8.dp),

    // Medium — buttons, cards (inner), status tags
    medium = RoundedCornerShape(12.dp),

    // Large — cards, bottom sheets, containers
    large = RoundedCornerShape(16.dp),

    // Extra large — full-width overlays, modals
    extraLarge = RoundedCornerShape(24.dp)
)

// Coin control checkbox radius
val CoinControlCheckboxRadius = RoundedCornerShape(4.dp)

// Full pill shape for protocol badges
val ProtocolBadgeShape = RoundedCornerShape(9999.dp)
