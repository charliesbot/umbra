package com.charliesbot.terminal

interface TerminalEngine {
    fun initialize(config: TerminalConfig): Boolean
    fun processInput(data: ByteArray)
    fun resize(cols: Int, rows: Int)
    fun getCell(row: Int, col: Int): Cell
    fun getDirtyCells(): List<CellUpdate>
    fun destroy()
}
