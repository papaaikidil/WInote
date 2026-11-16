package id.hananote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import id.hananote.data.ThemeMode
import id.hananote.data.ThemeSettings

private val LightColors = lightColorScheme(
    primary = Color(0xFF6E3A5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DDEA),
    onPrimaryContainer = Color(0xFF2C0E22),
    secondary = Color(0xFF2F43B5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E4FA),
    onSecondaryContainer = Color(0xFF0F1A5C),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF1A1C21),
    surface = Color(0xFFFBFBFC),
    onSurface = Color(0xFF1A1C21),
    surfaceVariant = Color(0xFFE6E7EB),
    onSurfaceVariant = Color(0xFF55596A),
    outline = Color(0xFFB4B7C2),
    outlineVariant = Color(0xFFDADCE2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8B6D3),
    onPrimary = Color(0xFF45173A),
    primaryContainer = Color(0xFF5A2B4C),
    onPrimaryContainer = Color(0xFFFCE3F0),
    secondary = Color(0xFFB3BEFF),
    onSecondary = Color(0xFF14207A),
    secondaryContainer = Color(0xFF2A3584),
    onSecondaryContainer = Color(0xFFE0E4FF),
    background = Color(0xFF121317),
    onBackground = Color(0xFFE4E5EA),
    surface = Color(0xFF1A1B20),
    onSurface = Color(0xFFE4E5EA),
    surfaceVariant = Color(0xFF292A31),
    onSurfaceVariant = Color(0xFFB2B4C0),
    outline = Color(0xFF6E7180),
    outlineVariant = Color(0xFF3A3C45),
)

private val Base = Typography()

/** Judul memakai huruf berkait supaya terasa seperti buku; teks lain tetap huruf sistem. */
val HanaTypography = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
)

@Composable
fun isDarkTheme(settings: ThemeSettings): Boolean = when (settings.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Tema aplikasi mengikuti pilihan warna aksen, mode terang/gelap, dan gaya huruf judul. */
@Composable
fun HanaTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val a = settings.accent
    val scheme = if (isDarkTheme(settings)) {
        DarkColors.copy(
            primary = Color(a.dark),
            onPrimary = Color(0xFF16171B),
            primaryContainer = Color(a.darkContainer),
            onPrimaryContainer = Color(0xFFF3F3F6),
        )
    } else {
        LightColors.copy(
            primary = Color(a.light),
            onPrimary = Color.White,
            primaryContainer = Color(a.lightContainer),
            onPrimaryContainer = Color(0xFF1A1C21),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = if (settings.serifTitles) HanaTypography else Base,
        content = content,
    )
}
