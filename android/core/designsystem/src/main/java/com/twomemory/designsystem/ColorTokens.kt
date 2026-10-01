package com.twomemory.designsystem

import androidx.compose.ui.graphics.Color

object TwoMemoryColors {
    // The warm-beige palette is shared by every feature surface. Keeping these
    // values in one place prevents the timeline, editor, album and map from
    // drifting into four subtly different products.
    val WarmBeigeBackground = Color(0xFFF8F4EB)
    val WarmBeigeSurface = Color(0xFFFFFCF7)
    val WarmBeigeInk = Color(0xFF37332F)
    val WarmBeigeMuted = Color(0xFF81786F)
    val WarmBeigeAccent = Color(0xFFD67B63)
    val WarmBeigeAccentSoft = Color(0xFFF3D8CD)
    val WarmBeigeSage = Color(0xFF788770)
    val WarmBeigeLine = Color(0xFFE2D5C8)
    val WarmBeigeNav = Color(0xFFF7EFF9)
    val WarmBeigePaperShadow = Color(0x1A8B776A)

    val PureWhiteBackground = Color(0xFFFFFFFF)
    val PureWhiteSurface = Color(0xFFFDFCFB)
    val PureWhiteInk = Color(0xFF2F3033)
    val PureWhiteMuted = Color(0xFF85878C)
    val PureWhiteAccent = Color(0xFFB97764)
    val PureWhiteLine = Color(0xFFE5E5E5)
}

enum class MoonLetterTheme { WARM_BEIGE, PURE_WHITE }
