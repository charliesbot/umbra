package com.charliesbot.terminal

data class Cell(
    val codepoint: Int = 0,
    val foreground: Int = 0xFFFFFF,
    val background: Int = 0x000000,
    val attributes: Int = 0,
) {
    companion object {
        const val ATTR_BOLD = 1
        const val ATTR_ITALIC = 2
        const val ATTR_UNDERLINE = 4
        const val ATTR_INVERSE = 8
    }
}
