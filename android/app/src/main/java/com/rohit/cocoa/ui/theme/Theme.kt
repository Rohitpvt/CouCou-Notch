package com.rohit.cocoa.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CocoaAccent,
    secondary = CocoaAccentDark,
    tertiary = MochiOrange,
    background = CocoaBgDark,
    surface = CocoaSurfaceDark,
    surfaceVariant = CocoaCardDark,
    onPrimary = CocoaTextPrimary,
    onSecondary = CocoaTextPrimary,
    onBackground = CocoaTextPrimary,
    onSurface = CocoaTextPrimary,
    onSurfaceVariant = CocoaTextSecondary,
    outline = CocoaCardBorder
)

@Composable
fun CocoaCompanionTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
