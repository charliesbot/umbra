package com.charliesbot.terminal

data class TerminalConfig(
    val initialCols: Int = 80,
    val initialRows: Int = 24,
    val scrollbackLines: Int = 10_000,
    val termType: String = "xterm-256color",
)
