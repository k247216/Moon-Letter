package com.twomemory.model

enum class NotePaperStyle(val label: String) { TAPE("贴贴"), RULED("横线"), LETTER("信笺") }
enum class NotePaperColor(val label: String) { CREAM("奶油"), ROSE("淡桃"), SAGE("嫩叶"), MIST("雾蓝"), LILAC("浅紫") }
data class NotePaperAppearance(val style: NotePaperStyle = NotePaperStyle.TAPE,
    val color: NotePaperColor = NotePaperColor.CREAM)
