package com.example.hbsz_app_android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 品牌配色方案 —— 对齐 Web 端 Element Plus 主题（主色 #1890FF）。
 *
 * 默认关闭 dynamicColor：Material You 取色会破坏双端品牌一致性，
 * 与 Web 端「主题色统一」的约束冲突（见 project_memory：BaseButton 需跟随
 * --el-color-primary 运行时切换，App 端对应固定品牌色）。
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = BgCard,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = PrimaryDark,
    onSecondary = BgCard,
    secondaryContainer = InfoContainer,
    onSecondaryContainer = TextRegular,
    tertiary = Success,
    onTertiary = BgCard,
    tertiaryContainer = SuccessContainer,
    onTertiaryContainer = SuccessText,
    error = Danger,
    onError = BgCard,
    errorContainer = DangerContainer,
    onErrorContainer = DangerText,
    background = BgPage,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgHover,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    outlineVariant = BorderLight,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgCard
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = DarkSurface,
    primaryContainer = PrimaryDarkContainer,
    onPrimaryContainer = PrimaryLight,
    secondary = Primary,
    onSecondary = DarkSurface,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextSecondary,
    tertiary = Success,
    onTertiary = DarkSurface,
    tertiaryContainer = Color(0xFF14301C),
    onTertiaryContainer = Success,
    error = Danger,
    onError = DarkSurface,
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFF7875),
    background = Color(0xFF14161B),
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF2E333D),
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = DarkSurface
)

/** 圆角体系：对齐 Web --radius-sm/base/md/lg（4/6/8/12px） */
val HbszShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun HbszTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 品牌一致优先，默认不跟随 Material You 取色
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = HbszShapes,
        content = content
    )
}
