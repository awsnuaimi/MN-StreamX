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
        fun open(environment: OrtEnvironment, file: File, optimize: Boolean = false,
            optimizedOutput: File? = null): MappedCaptionSession {
            require(optimizedOutput == null || optimize)
            val options = OrtSession.SessionOptions()
            try {
                options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1)
                options.setCPUArenaAllocator(false); options.setMemoryPatternOptimization(false)
                options.setOptimizationLevel(if (optimize) OrtSession.SessionOptions.OptLevel.EXTENDED_OPT
                    else OrtSession.SessionOptions.OptLevel.NO_OPT)
                // ORT 1.20 CSE dereferences raw INT64 scalar attributes without alignment
                // checks. This can raise SIGBUS on ARMv7 (upstream issue #26323).
                // Keep the other optimizers and our prepared vocabulary projection.
                options.addConfigEntry("optimization.disable_specified_optimizers", "CommonSubexpressionElimination")
                options.addConfigEntry("session.disable_prepacking", "1")
                options.addConfigEntry("session.intra_op.allow_spinning", "0")
                options.addConfigEntry("session.inter_op.allow_spinning", "0")
                if (optimizedOutput != null) {
                    options.setOptimizedModelFilePath(optimizedOutput.absolutePath)
                    options.addConfigEntry("session.optimized_model_external_initializers_file_name", "weights.bin")
                    options.addConfigEntry("session.optimized_model_external_initializers_min_size_in_bytes", "65536")
                }
                return MappedCaptionSession(environment.createSession(file.absolutePath, options), options)
            } catch (error: Throwable) { options.close(); throw error }
        }
    }
}