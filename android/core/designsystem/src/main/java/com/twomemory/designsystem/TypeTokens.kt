package com.twomemory.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object TwoMemoryTypography {
    val display = TextStyle(fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 34.sp)
    val title = TextStyle(fontFamily = FontFamily.Serif, fontSize = 21.sp, lineHeight = 29.sp)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp)
    val button = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp)

    fun material() = Typography(
        headlineMedium = display,
        titleLarge = title,
        bodyLarge = body,
        labelMedium = caption,
        labelLarge = button,
    )
}
