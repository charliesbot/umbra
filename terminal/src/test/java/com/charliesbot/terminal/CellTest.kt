package com.charliesbot.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class CellTest {

    @Test
    fun `default cell has expected values`() {
        val cell = Cell()
        assertEquals(0, cell.codepoint)
        assertEquals(0xFFFFFF, cell.foreground)
        assertEquals(0x000000, cell.background)
        assertEquals(0, cell.attributes)
    }

    @Test
    fun `attribute bitmask constants are correct`() {
        assertEquals(1, Cell.ATTR_BOLD)
        assertEquals(2, Cell.ATTR_ITALIC)
        assertEquals(4, Cell.ATTR_UNDERLINE)
        assertEquals(8, Cell.ATTR_INVERSE)
    }

    @Test
    fun `attributes can be combined with bitmask`() {
        val boldItalic = Cell.ATTR_BOLD or Cell.ATTR_ITALIC
        assertEquals(3, boldItalic)

        val cell = Cell(attributes = boldItalic)
        assertEquals(boldItalic, cell.attributes)
    }
}
