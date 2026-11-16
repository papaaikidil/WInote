package id.goresan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF4FA8FF)
val PanelBg = Color(0xF21E1E22)
val BarBg = Color(0xE6161618)
val Muted = Color(0xFF9A9AA2)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF173A5C),
    onPrimaryContainer = Color(0xFFD3E6FF),
    secondary = Color(0xFFF6C453),
    onSecondary = Color(0xFF3A2A00),
    background = Color(0xFF121214),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF1C1C20),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFB4B4BE),
    outline = Color(0xFF5E5E68),
    outlineVariant = Color(0xFF3A3A42),
)

@Composable
fun GoresanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
