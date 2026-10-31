package neth.iecal.curbox.clocationblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF245B46), onPrimary = Color.White,
    background = Color(0xFFFAFAF7), surface = Color(0xFFFAFAF7),
    surfaceVariant = Color(0xFFEEEEE8), onSurface = Color(0xFF1C201D),
    onSurfaceVariant = Color(0xFF596159), outline = Color(0xFF7D857D),
    outlineVariant = Color(0xFFD9DED5), secondaryContainer = Color(0xFFE0EBDD),
    onSecondaryContainer = Color(0xFF243D2C)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFADD5B9), onPrimary = Color(0xFF123B29),
    background = Color(0xFF141714), surface = Color(0xFF141714),
    surfaceVariant = Color(0xFF242A24), onSurface = Color(0xFFE5E8E1),
    onSurfaceVariant = Color(0xFFB7C0B5), outlineVariant = Color(0xFF3C453D),
    secondaryContainer = Color(0xFF304335), onSecondaryContainer = Color(0xFFD5EBD8)
)
@Composable
fun LocationBlockerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
