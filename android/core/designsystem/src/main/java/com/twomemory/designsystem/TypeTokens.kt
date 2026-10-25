package com.twomemory.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp

object TwoMemoryTypography {
    val journalFamily = FontFamily(Font(R.font.moonletter_wenkai))
    val display = TextStyle(fontFamily = journalFamily, fontSize = 27.sp, lineHeight = 34.sp)
    val title = TextStyle(fontFamily = journalFamily, fontSize = 21.sp, lineHeight = 29.sp)
    val body = TextStyle(fontFamily = journalFamily, fontSize = 17.sp, lineHeight = 27.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp)
    val button = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp)
    val metadata = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 15.sp)

    fun material() = Typography(
        headlineMedium = display,
        titleLarge = title,
        bodyLarge = body,
        labelMedium = caption,
        labelLarge = button,
    )
}
