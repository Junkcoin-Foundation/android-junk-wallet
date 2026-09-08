package junkwallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// ── Obsidian Cyber-UTXO Dark Color Scheme ──

private val JunkcoinDarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = OnPrimaryCyan,
    primaryContainer = PrimaryCyanContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,

    secondary = SecondaryEmerald,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryEmeraldContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = TertiaryAmber,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryAmberContainer,
    onTertiaryContainer = OnTertiaryContainer,

    error = ErrorCrimson,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    background = Background,
    onBackground = OnBackground,

    surface = ObsidianSurface,
    onSurface = TextHighEmphasis,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextMidEmphasis,

    surfaceTint = SurfaceTint,

    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,

    outline = Outline,
    outlineVariant = OutlineVariant
)

@Composable
fun JunkWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JunkcoinDarkColorScheme,
        typography = JunkcoinTypography,
        shapes = JunkcoinShapes,
        content = content
    )
}
