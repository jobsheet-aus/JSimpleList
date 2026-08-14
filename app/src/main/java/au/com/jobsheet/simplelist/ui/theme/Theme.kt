package au.com.jobsheet.simplelist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val JSimpleListColorScheme = lightColorScheme(
    primary = JsBlue,
    onPrimary = JsOnPrimary,
    secondary = JsCyan,
    background = JsBackground,
    onBackground = JsText,
    surface = JsSurface,
    onSurface = JsText,
    surfaceVariant = JsSurfaceVariant,
    onSurfaceVariant = JsMutedText,
    outline = JsBorder
)

@Composable
fun SimpleListTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JSimpleListColorScheme,
        typography = Typography,
        content = content
    )
}