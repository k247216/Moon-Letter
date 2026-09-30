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
            outline = TwoMemoryColors.WarmBeigeLine,
        )
    } else {
        lightColorScheme(
            background = TwoMemoryColors.PureWhiteBackground,
            surface = TwoMemoryColors.PureWhiteSurface,
            onBackground = TwoMemoryColors.PureWhiteInk,
            onSurface = TwoMemoryColors.PureWhiteInk,
            primary = TwoMemoryColors.PureWhiteAccent,
            outline = TwoMemoryColors.PureWhiteLine,
        )
    }
    MaterialTheme(colorScheme = colors, typography = TwoMemoryTypography.material(), content = content)
}
