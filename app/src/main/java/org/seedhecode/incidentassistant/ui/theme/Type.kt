package org.seedhecode.incidentassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.seedhecode.incidentassistant.R

// Static, OFL-licensed instances support offline rendering on Android API 23+.
val RobotoFlex = FontFamily(
    Font(R.font.roboto_flex_regular, FontWeight.Normal),
    Font(R.font.roboto_flex_medium, FontWeight.Medium)
)

// Optical size 48, regular 400: an editorial approximation, not proprietary Alfabet.
val RobotoFlexDisplay = FontFamily(Font(R.font.roboto_flex_display_regular, FontWeight.Normal))

private fun editorial(size: Int, leading: Int, tracking: Float = -0.3f) = TextStyle(
    fontFamily = RobotoFlexDisplay,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
    fontSize = size.sp,
    lineHeight = leading.sp,
    letterSpacing = tracking.sp
)

private fun uiText(size: Int, leading: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = RobotoFlex,
    fontWeight = weight,
    fontSynthesis = FontSynthesis.None,
    fontSize = size.sp,
    lineHeight = leading.sp,
    letterSpacing = 0.sp
)

// Every Material 3 role is explicit, including dialogs, tabs, fields and navigation labels.
val Typography = Typography(
    displayLarge = editorial(38, 44),
    displayMedium = editorial(34, 40),
    displaySmall = editorial(32, 38),
    headlineLarge = editorial(28, 34),
    headlineMedium = editorial(26, 32, -0.2f),
    headlineSmall = editorial(20, 26, -0.1f),
    titleLarge = uiText(18, 24, FontWeight.Medium),
    titleMedium = uiText(16, 24, FontWeight.Medium),
    titleSmall = uiText(14, 20, FontWeight.Medium),
    bodyLarge = uiText(16, 24),
    bodyMedium = uiText(14, 20),
    bodySmall = uiText(12, 16),
    labelLarge = uiText(14, 20, FontWeight.Medium),
    labelMedium = uiText(12, 16, FontWeight.Medium),
    labelSmall = uiText(11, 14, FontWeight.Medium)
)
