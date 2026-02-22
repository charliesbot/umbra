package com.charliesbot.terminal

internal interface NativeBridgeApi {
    fun nativeCreateSession(cols: Int, rows: Int): Long
    fun nativeProcessInput(sessionId: Long, data: ByteArray)
    fun nativeResize(sessionId: Long, cols: Int, rows: Int)
    fun nativeGetSessionState(sessionId: Long): String
    fun nativeDestroy(sessionId: Long)
}

internal class NativeBridge : NativeBridgeApi {
    override external fun nativeCreateSession(cols: Int, rows: Int): Long
    override external fun nativeProcessInput(sessionId: Long, data: ByteArray)
    override external fun nativeResize(sessionId: Long, cols: Int, rows: Int)
    override external fun nativeGetSessionState(sessionId: Long): String
    override external fun nativeDestroy(sessionId: Long)

    companion object {
        init {
            System.loadLibrary("terminal")
        }
    }
}
