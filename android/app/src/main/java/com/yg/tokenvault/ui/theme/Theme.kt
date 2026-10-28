package com.yg.tokenvault.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 主题色调：跟随系统 / 强制深 / 强制浅 */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * 自定义色板 —— Material3 的 scheme 只有一层语义槽位，
 * 这里额外补上边框、次级文字等本项目高频用到的颜色。
 */
data class AppPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val border: Color,
    val borderHi: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accent2: Color,
    val danger: Color,
    val warning: Color,
    val success: Color
)

val DarkPalette = AppPalette(
    isDark = true,
    bg = BgDark,
    surface = SurfaceDark,
    surfaceHi = SurfaceHiDark,
    border = BorderDark,
    borderHi = BorderHiDark,
    text = TextDark,
    text2 = Text2Dark,
    text3 = Text3Dark,
    accent = Accent,
    accent2 = Accent2,
    danger = Danger,
    warning = Warning,
    success = Success
)

val LightPalette = AppPalette(
    isDark = false,
    bg = BgLight,
    surface = SurfaceLight,
    surfaceHi = SurfaceHiLight,
    border = BorderLight,
    borderHi = BorderHiLight,
    text = TextLight,
    text2 = Text2Light,
    text3 = Text3Light,
    accent = Accent,
    accent2 = Accent2,
    danger = Danger,
    warning = Warning,
    success = Success
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent2,
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceHiDark,
    onSurfaceVariant = Text2Dark,
    outline = BorderHiDark,
    error = Danger
)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent2,
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceHiLight,
    onSurfaceVariant = Text2Light,
    outline = BorderHiLight,
    error = Danger
)

@Composable
fun TokenVaultTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (dark) DarkPalette else LightPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 状态栏 / 导航栏透明，内容自己画到边
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content
        )
    }
}