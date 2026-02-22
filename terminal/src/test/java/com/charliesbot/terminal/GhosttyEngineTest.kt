package com.charliesbot.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GhosttyEngineTest {

    private lateinit var engine: GhosttyEngine

    private class FakeBridge : NativeBridgeApi {
        override fun nativeCreateSession(cols: Int, rows: Int): Long = 42L
        override fun nativeProcessInput(sessionId: Long, data: ByteArray) {}
        override fun nativeResize(sessionId: Long, cols: Int, rows: Int) {}
        override fun nativeGetSessionState(sessionId: Long): String = "{}"
        override fun nativeDestroy(sessionId: Long) {}
    }

    @Before
    fun setUp() {
        engine = GhosttyEngine(FakeBridge())
    }

    @Test(expected = IllegalStateException::class)
    fun `processInput before initialize throws`() {
        engine.processInput(byteArrayOf(0x1b))
    }

    @Test(expected = IllegalStateException::class)
    fun `resize before initialize throws`() {
        engine.resize(80, 24)
    }

    @Test(expected = IllegalStateException::class)
    fun `getCell before initialize throws`() {
        engine.getCell(0, 0)
    }

    @Test(expected = IllegalStateException::class)
    fun `getDirtyCells before initialize throws`() {
        engine.getDirtyCells()
    }

    @Test
    fun `initialize with default config returns true`() {
        assertTrue(engine.initialize(TerminalConfig()))
    }

    @Test
    fun `initialize called twice returns false`() {
        assertTrue(engine.initialize(TerminalConfig()))
        assertFalse(engine.initialize(TerminalConfig()))
    }

    @Test
    fun `destroy is idempotent`() {
        engine.initialize(TerminalConfig())
        engine.destroy()
        engine.destroy()
    }

    @Test
    fun `engine can be reused after destroy`() {
        engine.initialize(TerminalConfig())
        engine.destroy()
        assertTrue(engine.initialize(TerminalConfig()))
    }

    @Test
    fun `getCell returns default cell after initialize`() {
        engine.initialize(TerminalConfig())
        val cell = engine.getCell(0, 0)
        assertEquals(Cell(), cell)
    }

    @Test
    fun `getDirtyCells returns empty list after initialize`() {
        engine.initialize(TerminalConfig())
        val dirty = engine.getDirtyCells()
        assertTrue(dirty.isEmpty())
    }
}
