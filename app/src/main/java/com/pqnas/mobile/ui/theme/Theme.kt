package com.pqnas.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class PqnasAppTheme(
    val storageKey: String,
    val label: String,
    val description: String
) {
    Dark(
        storageKey = "dark",
        label = "Dark",
        description = "Nexus Cloud dark cyan"
    ),
    Bright(
        storageKey = "bright",
        label = "Bright",
        description = "Light readable mode"
    ),
    CpunkOrange(
        storageKey = "cpunk_orange",
        label = "CPUNK Orange",
        description = "Dark orange CPUNK style"
    ),
    WinClassic(
        storageKey = "win_classic",
        label = "Windows Classic",
        description = "Light gray classic UI"
    );

    companion object {
        fun fromStorageKey(value: String?): PqnasAppTheme {
            return values().firstOrNull { it.storageKey == value } ?: Dark
        }
    }
}

// PQNAS_ANDROID_THEME_AWARE_V1:
// Android uses the same public theme ids as the server theme system:
// dark, bright, cpunk_orange and win_classic. These are native Compose
// ColorSchemes, not WebView/CSS, so untrusted server CSS cannot affect app UI.
private val PqnasDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00F0F8),
    onPrimary = Color(0xFF001014),
    secondary = Color(0xFF64B5FF),
    onSecondary = Color(0xFF001426),
    tertiary = Color(0xFFFFBE00),
    onTertiary = Color(0xFF241800),
    background = Color(0xFF050712),
    onBackground = Color(0xFFE7FCFF),
    surface = Color(0xFF0C1322),
    onSurface = Color(0xFFE7FCFF),
    surfaceVariant = Color(0xFF121B2D),
    onSurfaceVariant = Color(0xFF9FE8EF),
    outline = Color(0xFF245B66),
    error = Color(0xFFFF5A5A),
    onError = Color.White
)

private val PqnasBrightColorScheme = lightColorScheme(
    primary = Color(0xFF00788C),
    onPrimary = Color.White,
    secondary = Color(0xFF286EC8),
    onSecondary = Color.White,
    tertiary = Color(0xFFBE7800),
    onTertiary = Color.White,
    background = Color(0xFFF5F7FF),
    onBackground = Color(0xFF0B1020),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFF0F3FA),
    onSurfaceVariant = Color(0xFF3D4658),
    outline = Color(0xFFB8BECC),
    error = Color(0xFFBE2828),
    onError = Color.White
)

private val PqnasCpunkOrangeColorScheme = darkColorScheme(
    primary = Color(0xFFFF7A18),
    onPrimary = Color(0xFF1B0B00),
    secondary = Color(0xFFFFBE00),
    onSecondary = Color(0xFF251800),
    tertiary = Color(0xFFFF9B50),
    onTertiary = Color(0xFF231000),
    background = Color(0xFF120B06),
    onBackground = Color(0xFFFFE9DA),
    surface = Color(0xFF1B1009),
    onSurface = Color(0xFFFFE9DA),
    surfaceVariant = Color(0xFF2B170B),
    onSurfaceVariant = Color(0xFFFFB179),
    outline = Color(0xFF8A4318),
    error = Color(0xFFFF5A5A),
    onError = Color.White
)

private val PqnasWinClassicColorScheme = lightColorScheme(
    primary = Color(0xFF0B4EA2),
    onPrimary = Color.White,
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color.White,
    tertiary = Color(0xFF9A6B00),
    onTertiary = Color.White,
    background = Color(0xFFCFCFCF),
    onBackground = Color.Black,
    surface = Color(0xFFE6E6E6),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF1F1F1F),
    outline = Color(0xFF666666),
    error = Color(0xFF5F1515),
    onError = Color.White
)

@Composable
fun PQNASTheme(
    appTheme: PqnasAppTheme = PqnasAppTheme.Dark,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        PqnasAppTheme.Dark -> PqnasDarkColorScheme
        PqnasAppTheme.Bright -> PqnasBrightColorScheme
        PqnasAppTheme.CpunkOrange -> PqnasCpunkOrangeColorScheme
        PqnasAppTheme.WinClassic -> PqnasWinClassicColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
