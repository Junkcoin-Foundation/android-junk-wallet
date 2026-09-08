package junkwallet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Stitch Typography: Obsidian Cyber-UTXO ──
// Headlines: Geist (dense numerical kerning, clean glyphs)
// Body: Inter (legible across labels, alerts, recovery flows)
// Mono: JetBrains Mono (addresses, TXIDs, amounts, derivation paths)

val GeistFamily = FontFamily.Default
val InterFamily = FontFamily.Default
val JetBrainsMonoFamily = FontFamily.Monospace

val JunkcoinTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 44.sp,
        letterSpacing = (-0.025).sp
    ),
    displayMedium = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).sp
    ),
    displaySmall = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = (-0.015).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GeistFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = 0.01.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.01.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.02.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 12.sp,
        letterSpacing = 0.08.sp
    )
)
