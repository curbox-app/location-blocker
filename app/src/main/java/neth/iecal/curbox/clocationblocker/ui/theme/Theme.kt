package neth.iecal.curbox.clocationblocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every role is set so no Material baseline purple leaks into dialogs, menus, or containers.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6B4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDC), onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4E6358), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7DF), onSecondaryContainer = Color(0xFF1A2D24),
    tertiary = Color(0xFF3F6375), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC3E8FD), onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7FAF7), onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF7FAF7), onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DE), onSurfaceVariant = Color(0xFF545E58),
    surfaceTint = Color(0xFF1F6B4F),
    outline = Color(0xFF79847D), outlineVariant = Color(0xFFC8D1CA),
    inverseSurface = Color(0xFF2E312F), inverseOnSurface = Color(0xFFEFF2EE), inversePrimary = Color(0xFF8FD5B4),
    surfaceBright = Color(0xFFF7FAF7), surfaceDim = Color(0xFFD7DBD7),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F0),
    surfaceContainer = Color(0xFFEAEFEB), surfaceContainerHigh = Color(0xFFE4E9E5),
    surfaceContainerHighest = Color(0xFFDEE4DF),
    scrim = Color.Black
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FD5B4), onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513B), onPrimaryContainer = Color(0xFFABF2CF),
    secondary = Color(0xFFB4CCBF), onSecondary = Color(0xFF20352B),
    secondaryContainer = Color(0xFF364B41), onSecondaryContainer = Color(0xFFD0E8DA),
    tertiary = Color(0xFFA7CCE0), onTertiary = Color(0xFF0B3445),
    tertiaryContainer = Color(0xFF264B5C), onTertiaryContainer = Color(0xFFC3E8FD),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111412), onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF111412), onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceTint = Color(0xFF8FD5B4),
    outline = Color(0xFF89938B), outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFE1E3E0), inverseOnSurface = Color(0xFF2E312F), inversePrimary = Color(0xFF1F6B4F),
    surfaceBright = Color(0xFF373A38), surfaceDim = Color(0xFF111412),
    surfaceContainerLowest = Color(0xFF0C0F0D), surfaceContainerLow = Color(0xFF191C1A),
    surfaceContainer = Color(0xFF1D201E), surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerHighest = Color(0xFF323633),
    scrim = Color.Black
)
@Composable
fun LocationBlockerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography, content = content)
}
