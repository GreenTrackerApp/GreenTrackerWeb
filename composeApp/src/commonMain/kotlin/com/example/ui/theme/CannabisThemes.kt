package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class CannabisTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val primaryColor: Color
) {
    CLASSIC_HERBAL("classic_herbal", "Classic Herbal", true, Color(0xFF4E7E5A)),
    MIDNIGHT_HAZE("midnight_haze", "Midnight Haze", true, Color(0xFF5DF273)),
    PURPLE_HAZE("purple_haze", "Purple Haze", true, Color(0xFFC084FC)),
    GOLDEN_MARY("golden_mary", "Golden Mary", false, Color(0xFFD97706)),
    DISKRET_DARK("diskret_dark", "Diskret Dark", true, Color(0xFF94A3B8)),
    DISKRET_WHITE("diskret_white", "Diskret White", false, Color(0xFF334155)),
    PRIDE("pride", "Pride", true, Color(0xFFFF0000))
}

val ClassicHerbalColors = darkColorScheme(
    primary = Color(0xFFA8D5BA),
    onPrimary = Color(0xFF0F1410),
    primaryContainer = Color(0xFF384B3B),
    onPrimaryContainer = Color(0xFFE1E3DF),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF0F1410),
    secondaryContainer = Color(0xFF1C221D),
    onSecondaryContainer = Color(0xFF8BA491),
    tertiary = Color(0xFFE2C044),
    onTertiary = Color(0xFF3A3000),
    tertiaryContainer = Color(0xFF55470B),
    onTertiaryContainer = Color(0xFFFFF0B3),
    background = Color(0xFF0F1410),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF1C221D),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF2D382F),
    onSurfaceVariant = Color(0xFF8BA491),
    outline = Color(0xFF2D382F)
)

val MidnightHazeColors = darkColorScheme(
    primary = Color(0xFF5DF273),
    onPrimary = Color(0xFF00390E),
    primaryContainer = Color(0xFF005318),
    onPrimaryContainer = Color(0xFF8DFF9A),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF00354E),
    tertiaryContainer = Color(0xFF004D71),
    onTertiaryContainer = Color(0xFFC2E7FF),
    background = Color(0xFF090D0A),
    onBackground = Color(0xFFF1F5F1),
    surface = Color(0xFF121814),
    onSurface = Color(0xFFF1F5F1),
    surfaceVariant = Color(0xFF1C241E),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B)
)

val PurpleHazeColors = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF4C0585),
    primaryContainer = Color(0xFF6B21A8),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFF4ADE80),
    onSecondary = Color(0xFF003815),
    secondaryContainer = Color(0xFF065F2C),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFF472B6),
    onTertiary = Color(0xFF50002A),
    tertiaryContainer = Color(0xFF701A47),
    onTertiaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF120E1A),
    onBackground = Color(0xFFF3E8FF),
    surface = Color(0xFF1A1526),
    onSurface = Color(0xFFF3E8FF),
    surfaceVariant = Color(0xFF261F37),
    onSurfaceVariant = Color(0xFFE2D4F0),
    outline = Color(0xFF8B5CF6)
)

val GoldenMaryColors = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFF15803D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEDD5),
    onTertiaryContainer = Color(0xFF7C2D12),
    background = Color(0xFFFCF8F2),
    onBackground = Color(0xFF1F1D1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1D1A),
    surfaceVariant = Color(0xFFF5EBE0),
    onSurfaceVariant = Color(0xFF4F463E),
    outline = Color(0xFFB4A69A)
)

val DiskretDarkColors = darkColorScheme(
    primary = Color(0xFF94A3B8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF1F5F9),
    secondary = Color(0xFF64748B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

val DiskretWhiteColors = lightColorScheme(
    primary = Color(0xFF334155),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1F5F9),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1)
)

val PrideColors = darkColorScheme(
    primary = Color(0xFFFFD700),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF330000),
    onPrimaryContainer = Color(0xFFFFD700),
    secondary = Color(0xFF00FFFF),
    onSecondary = Color(0xFF0F172A),
    tertiary = Color(0xFFFFFF00),
    background = Color(0xFF0F0F1A),
    onBackground = Color(0xFFFFFF00),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFFFFF00),
    surfaceVariant = Color(0xFF2E2E4E),
    onSurfaceVariant = Color(0xFF00FFFF),
    outline = Color(0xFF4A00E0)
)

fun getColorsForTheme(theme: CannabisTheme): ColorScheme {
    return when (theme) {
        CannabisTheme.CLASSIC_HERBAL -> ClassicHerbalColors
        CannabisTheme.MIDNIGHT_HAZE -> MidnightHazeColors
        CannabisTheme.PURPLE_HAZE -> PurpleHazeColors
        CannabisTheme.GOLDEN_MARY -> GoldenMaryColors
        CannabisTheme.DISKRET_DARK -> DiskretDarkColors
        CannabisTheme.DISKRET_WHITE -> DiskretWhiteColors
        CannabisTheme.PRIDE -> PrideColors
    }
}
