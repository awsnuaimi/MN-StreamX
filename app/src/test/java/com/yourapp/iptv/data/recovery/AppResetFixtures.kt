package com.yourapp.iptv.data.recovery

import java.io.IOException

internal class ResetMemory : AppResetJournal {
    var marked = false
    var failRead = false
    var failWrite = false
    var writes = 0
    var beforeWrite: () -> Unit = {}
    override fun pending(): Boolean {
        if (failRead) throw IOException("Synthetic reset status failure")
        return marked
    }
    override fun mark() {
        beforeWrite();if (failWrite) throw IOException("Synthetic reset intent failure")
        marked = true;writes++
    }
}
internal class ResetEraser : AppDataEraser {
    var calls = 0
    var accepted = true
    var failure: Exception? = null
    var before: () -> Unit = {}
    override fun request(): Boolean {
        calls++;before();failure?.let { throw it };return accepted
    }
}