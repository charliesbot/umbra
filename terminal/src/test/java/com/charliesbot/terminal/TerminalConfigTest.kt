package com.charliesbot.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalConfigTest {

    @Test
    fun `default values are correct`() {
        val config = TerminalConfig()
        assertEquals(80, config.initialCols)
        assertEquals(24, config.initialRows)
        assertEquals(10_000, config.scrollbackLines)
        assertEquals("xterm-256color", config.termType)
    }

    @Test
    fun `copy with modified values works`() {
        val config = TerminalConfig()
        val modified = config.copy(initialCols = 120, initialRows = 40)
        assertEquals(120, modified.initialCols)
        assertEquals(40, modified.initialRows)
        assertEquals(10_000, modified.scrollbackLines)
        assertEquals("xterm-256color", modified.termType)
    }
}
