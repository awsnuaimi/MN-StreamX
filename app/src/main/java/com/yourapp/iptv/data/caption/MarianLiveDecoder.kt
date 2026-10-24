package com.yourapp.iptv.data.caption

import ai.onnxruntime.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** One hypothesis per token. Retains only encoder cache and the most recent decoder cache. */
internal class MarianLiveDecoder(private val env: OrtEnvironment, private val config: MarianModelConfig,
    private val firstSession: () -> OrtSession, private val cachedSession: () -> OrtSession,
    private val run: (OrtSession, Map<String, OnnxTensorLike>) -> OrtSession.Result) {
    suspend fun decode(mask: OnnxTensor, hidden: OnnxTensor, maxTokens: Int): List<Long> {
        require(maxTokens in 1..72)
        val tokens = ArrayList<Long>(maxTokens)
        val zeros = HashMap<String, OnnxTensor>()
        var first: OrtSession.Result? = null
        var previous: OrtSession.Result? = null
        try {
            for (step in 0 until maxTokens) {
                currentCoroutineContext().ensureActive()
                val session = if (step == 0) firstSession() else cachedSession()
                val token = tokens.lastOrNull() ?: config.start
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1)).use { ids ->
                    OnnxTensor.createTensor(env, booleanArrayOf(step > 0)).use { branch ->
                        val feed = HashMap<String, OnnxTensorLike>()
                        feed["input_ids"] = ids
                        if ("encoder_attention_mask" in session.inputNames) feed["encoder_attention_mask"] = mask
                        if ("encoder_hidden_states" in session.inputNames) feed["encoder_hidden_states"] = hidden
                        if ("use_cache_branch" in session.inputNames) feed["use_cache_branch"] = branch
                        for (name in session.inputNames.filter { it.startsWith("past_key_values.") }) {
                            val owner = if (name.contains(".encoder.")) first else previous
                            val value = owner?.get(name.replace("past_key_values.", "present."))?.orElse(null) as? OnnxTensor
                            feed[name] = value ?: zeros.getOrPut(name) {
                                OnnxTensor.createTensor(env, FloatBuffer.allocate(0),
                                    longArrayOf(1, config.heads.toLong(), 0, (config.width / config.heads).toLong()))
                            }
                            check(step == 0 || value != null) { "Decoder cache missing: $name" }
                        }
                        val output = run(session, feed)
                        if (first == null) first = output
                        if (previous != null && previous !== first) previous!!.close()
                        previous = output
                        val logits = output.get("logits").orElseThrow {
                            IllegalStateException("Marian scores unavailable") } as OnnxTensor
                        val selected = MarianGreedySelection.next(logits.floatBuffer, config.vocabulary,
                            config.pad, config.start, tokens)
                        if (selected == config.eos) return tokens
                        tokens += selected
                    }
                }
            }
            return tokens
        } finally {
            if (previous !== first) previous?.close()
            first?.close()
            zeros.values.forEach { it.close() }
        }
    }
}