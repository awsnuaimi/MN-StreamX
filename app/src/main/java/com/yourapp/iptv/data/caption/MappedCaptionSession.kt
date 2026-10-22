package com.yourapp.iptv.data.caption

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/** ORT owns the external-data mappings and releases them when its session closes. */
internal class MappedCaptionSession private constructor(val session: OrtSession,
    private val options: OrtSession.SessionOptions) : AutoCloseable {
    private var closed = false
    override fun close() {
        if (closed) return
        closed = true
        try { session.close() } finally { options.close() }
    }
    companion object {
        fun open(environment: OrtEnvironment, file: File): MappedCaptionSession {
            val options = OrtSession.SessionOptions()
            try {
                options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
                options.setCPUArenaAllocator(false); options.setMemoryPatternOptimization(false)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                options.addConfigEntry("session.disable_prepacking", "1")
                options.addConfigEntry("session.intra_op.allow_spinning", "0")
                options.addConfigEntry("session.inter_op.allow_spinning", "0")
                return MappedCaptionSession(environment.createSession(file.absolutePath, options), options)
            } catch (error: Throwable) { options.close(); throw error }
        }
    }
}