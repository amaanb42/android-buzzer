package com.amaanb.androidbuzzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IdleBackgroundLight = Color(0xFFFFCDD2)
val RingingBackgroundLight = Color(0xFFBBDEFB)
val IdleBackgroundDark = Color(0xFF5C1F26)
val RingingBackgroundDark = Color(0xFF12344F)

val IdleButtonLight = Color(0xFF8C1D18)
val RingingButtonLight = Color(0xFF0B57D0)
val IdleButtonDark = Color(0xFFFFB4AB)
val RingingButtonDark = Color(0xFFA8C7FA)

private val LightColors = lightColorScheme(
    primary = IdleButtonLight,
    onPrimary = Color.White,
    primaryContainer = IdleBackgroundLight,
    onPrimaryContainer = Color(0xFF410002),
    secondary = RingingButtonLight,
    onSecondary = Color.White,
    secondaryContainer = RingingBackgroundLight,
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF526600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7F57B),
    onTertiaryContainer = Color(0xFF171E00),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF251918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
)

private val DarkColors = darkColorScheme(
    primary = IdleButtonDark,
    onPrimary = Color(0xFF690005),
    primaryContainer = IdleBackgroundDark,
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = RingingButtonDark,
    onSecondary = Color(0xFF062E6F),
    secondaryContainer = RingingBackgroundDark,
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = Color(0xFFBCD05E),
    onTertiary = Color(0xFF293500),
    tertiaryContainer = Color(0xFF3D4D00),
    onTertiaryContainer = Color(0xFFD7F57B),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF1C1110),
    onSurface = Color(0xFFF1DFDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
)

private val BuzzerTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BuzzerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BuzzerTypography,
        shapes = Shapes(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
