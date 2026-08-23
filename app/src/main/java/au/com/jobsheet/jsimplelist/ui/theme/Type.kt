package au.com.jobsheet.jsimplelist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import au.com.jobsheet.jsimplelist.R

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private val DefaultTypography = Typography()

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(
        fontFamily = ManropeFontFamily
    ),
    displayMedium = DefaultTypography.displayMedium.copy(
        fontFamily = ManropeFontFamily
    ),
    displaySmall = DefaultTypography.displaySmall.copy(
        fontFamily = ManropeFontFamily
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontFamily = ManropeFontFamily
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontFamily = ManropeFontFamily
    ),
    headlineSmall = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleLarge = DefaultTypography.titleLarge.copy(
        fontFamily = ManropeFontFamily
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    titleSmall = DefaultTypography.titleSmall.copy(
        fontFamily = ManropeFontFamily
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = DefaultTypography.bodySmall.copy(
        fontFamily = ManropeFontFamily
    ),
    labelLarge = TextStyle(
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = DefaultTypography.labelMedium.copy(
        fontFamily = ManropeFontFamily
    ),
    labelSmall = DefaultTypography.labelSmall.copy(
        fontFamily = ManropeFontFamily
    )
)