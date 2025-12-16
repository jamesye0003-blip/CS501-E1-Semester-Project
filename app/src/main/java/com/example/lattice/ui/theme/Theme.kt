package com.example.lattice.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 应用Lattice的主色板，分别用于深色和浅色模式。
 * 这些ColorScheme配置定义了Material 3主题的基础色彩，包括primary/secondary/tertiary等主色和背景、表面、错误等语义色彩。
 * 所有色值均在Color.kt集中管理，保证主题一致性与可维护性。
 * 
 * The main color palettes for the Lattice app, used for dark and light themes.
 * These ColorScheme configurations define the base colors of the Material 3 theme,
 * including primary/secondary/tertiary colors as well as semantic colors such as background, surface, and error.
 * All color values are centrally managed in Color.kt to ensure theme consistency and maintainability.
 *
 * - DarkColorScheme:   深色模式下的配色方案。
 *                      Color palette for dark mode.
 * - LightColorScheme:  浅色（默认）模式下的配色方案。
 *                      Color palette for light (default) mode.
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onError = OnErrorDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onError = OnErrorLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

/**
 * LatticeTheme 是应用顶层 Composable，用于设置全局的主题（颜色和字体）。
 * 
 * LatticeTheme is the top-level composable of the app, responsible for configuring the global theme (colors and typography).
 * 
 * @param 
 * - darkTheme: Whether to enable dark mode. Defaults to following the system theme.
 * - dynamicColor:  Whether to enable dynamic color (available on Android 12+).
 *                  Disabled by default to ensure consistency of task priority semantic colors.
 * - content: The composable content slot.
 * 
 * LatticeTheme 自动根据参数选择 LightColorScheme 或 DarkColorScheme，并设置状态栏颜色与模式。
 * 
 * LatticeTheme automatically selects LightColorScheme or DarkColorScheme based on
 * the provided parameters, and configures the system status bar color and appearance.
 */
@Composable
fun LatticeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Make the status bar blend with the background.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}