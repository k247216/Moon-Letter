package com.twomemory.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun TwoMemoryTheme(theme: MoonLetterTheme = MoonLetterTheme.WARM_BEIGE, content: @Composable () -> Unit) {
    val beige = theme == MoonLetterTheme.WARM_BEIGE
    val colors = if (beige) {
        lightColorScheme(
            background = TwoMemoryColors.WarmBeigeBackground,
            surface = TwoMemoryColors.WarmBeigeSurface,
            onBackground = TwoMemoryColors.WarmBeigeInk,
            onSurface = TwoMemoryColors.WarmBeigeInk,
            primary = TwoMemoryColors.WarmBeigeAccent,
            primaryContainer = TwoMemoryColors.WarmBeigeAccentSoft,
            onPrimaryContainer = TwoMemoryColors.WarmBeigeInk,
            secondary = TwoMemoryColors.WarmBeigeSage,
            secondaryContainer = TwoMemoryColors.WarmBeigeAccentSoft,
            onSecondaryContainer = TwoMemoryColors.WarmBeigeInk,
            tertiary = TwoMemoryColors.WarmBeigeSage,
            onSurfaceVariant = TwoMemoryColors.WarmBeigeMuted,
            surfaceVariant = TwoMemoryColors.WarmBeigeNav,
            outline = TwoMemoryColors.WarmBeigeLine,
            outlineVariant = TwoMemoryColors.WarmBeigeLine,
        )
    } else {
        lightColorScheme(
            background = TwoMemoryColors.PureWhiteBackground,
            surface = TwoMemoryColors.PureWhiteSurface,
            onBackground = TwoMemoryColors.PureWhiteInk,
            onSurface = TwoMemoryColors.PureWhiteInk,
            primary = TwoMemoryColors.PureWhiteAccent,
            primaryContainer = TwoMemoryColors.PureWhiteLine,
            onPrimaryContainer = TwoMemoryColors.PureWhiteInk,
            secondary = TwoMemoryColors.PureWhiteAccent,
            secondaryContainer = TwoMemoryColors.PureWhiteLine,
            onSecondaryContainer = TwoMemoryColors.PureWhiteInk,
            tertiary = TwoMemoryColors.WarmBeigeSage,
            onSurfaceVariant = TwoMemoryColors.PureWhiteMuted,
            surfaceVariant = TwoMemoryColors.PureWhiteSurface,
            outline = TwoMemoryColors.PureWhiteLine,
            outlineVariant = TwoMemoryColors.PureWhiteLine,
        )
    }
    MaterialTheme(colorScheme = colors, typography = TwoMemoryTypography.material(), content = content)
}
