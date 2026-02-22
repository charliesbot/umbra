package com.charliesbot.terminal

class GhosttyEngine internal constructor(
    private val bridge: NativeBridgeApi,
) : TerminalEngine {

    constructor() : this(NativeBridge())

    private var sessionId: Long = 0L
    private var initialized = false

    override fun initialize(config: TerminalConfig): Boolean {
        if (initialized) return false
        sessionId = bridge.nativeCreateSession(config.initialCols, config.initialRows)
        initialized = true
        return true
    }

    override fun processInput(data: ByteArray) {
        check(initialized) { "Engine not initialized" }
        bridge.nativeProcessInput(sessionId, data)
    }

    override fun resize(cols: Int, rows: Int) {
        check(initialized) { "Engine not initialized" }
        bridge.nativeResize(sessionId, cols, rows)
    }

    override fun getCell(row: Int, col: Int): Cell {
        check(initialized) { "Engine not initialized" }
        // TODO: Read cell data from native VT state
        return Cell()
    }

    override fun getDirtyCells(): List<CellUpdate> {
        check(initialized) { "Engine not initialized" }
        // TODO: Read dirty cells from native VT state
        return emptyList()
    }

    override fun destroy() {
        if (!initialized) return
        bridge.nativeDestroy(sessionId)
        sessionId = 0L
        initialized = false
    }
}
