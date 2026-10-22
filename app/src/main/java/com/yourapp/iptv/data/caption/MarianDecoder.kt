package com.yourapp.iptv.data.caption

import ai.onnxruntime.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Incremental decoding retains the first encoder cache and only the latest decoder cache. */
internal class MarianDecoder(private val environment: OrtEnvironment, private val session: OrtSession,
    private val run: (OrtSession, Map<String, OnnxTensorLike>) -> OrtSession.Result) {
    suspend fun decode(mask: OnnxTensor, hidden: OnnxTensor, startId: Long = 64109, eos: Long = 0): List<Long> {
        val generated = ArrayList<Long>()
        val zeros = session.inputNames.filter { it.startsWith("past_key_values.") }.associateWith {
            OnnxTensor.createTensor(environment, FloatBuffer.allocate(0), longArrayOf(1, 8, 0, 64))
        }
        var first: OrtSession.Result? = null
        var previous: OrtSession.Result? = null
        try {
            var token = startId
            for (step in 0 until 72) {
                currentCoroutineContext().ensureActive()
                OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1)).use { decoderIds ->
                OnnxTensor.createTensor(environment, booleanArrayOf(step > 0)).use { cached ->
                    val feed = mutableMapOf<String, OnnxTensorLike>("input_ids" to decoderIds,
                        "encoder_attention_mask" to mask, "encoder_hidden_states" to hidden, "use_cache_branch" to cached)
                    for ((name, empty) in zeros) {
                        val owner = if (name.contains(".encoder.")) first else previous
                        feed[name] = owner?.get(name.replace("past_key_values.", "present."))?.orElse(null) as? OnnxTensor ?: empty
                    }
                    val output = run(session, feed)
                    if (first == null) first = output
                    if (previous != null && previous !== first) previous!!.close()
                    previous = output
                    token = (output.get(MarianPreparedProjection.TOKEN_OUTPUT).orElseThrow {
                        IllegalStateException("Missing prepared decoder token") } as OnnxTensor).longBuffer.get(0)
                    check(token in 0 until startId) { "Invalid decoder token" }
                } }
                if (token == eos) break
                generated += token
            }
        } finally {
            if (previous !== first) previous?.close()
            first?.close(); zeros.values.forEach { it.close() }
        }
        return generated
    }
}