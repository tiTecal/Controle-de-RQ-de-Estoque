package br.com.tecal.almoxarifado.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TecalNavy = Color(0xFF0A2E4D)
val TecalNavyDark = Color(0xFF061D33)
val TecalNavyLight = Color(0xFF144B7A)
val TecalBlueAccent = Color(0xFF0284C7)
val TecalBlueSubtle = Color(0xFFE0F2FE)
val TecalSuccessGreen = Color(0xFF15803D)
val TecalSuccessEmerald = Color(0xFF10B981)
val TecalSuccessBg = Color(0xFFDCFCE7)
val TecalWarningAmber = Color(0xFFB45309)
val TecalWarningBg = Color(0xFFFEF3C7)
val TecalErrorRed = Color(0xFFB91C1C)
val TecalErrorBg = Color(0xFFFEE2E2)
val TecalSurfaceBg = Color(0xFFF1F5F9)
val TecalCardBorder = Color(0xFFE2E8F0)
val TecalTextPrimary = Color(0xFF0F172A)
val TecalTextSecondary = Color(0xFF475569)
val TecalTextMuted = Color(0xFF64748B)

private val TecalLightColorScheme = lightColorScheme(
    primary = TecalNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8F7),
    onPrimaryContainer = TecalNavyDark,
    secondary = TecalBlueAccent,
    onSecondary = Color.White,
    secondaryContainer = TecalBlueSubtle,
    onSecondaryContainer = Color(0xFF075985),
    tertiary = TecalSuccessGreen,
    onTertiary = Color.White,
    background = TecalSurfaceBg,
    onBackground = TecalTextPrimary,
    surface = Color.White,
    onSurface = TecalTextPrimary,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = TecalTextSecondary,
    error = TecalErrorRed,
    onError = Color.White
)

@Composable
fun TecalAlmoxarifadoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TecalLightColorScheme,
        content = content
    )
}
