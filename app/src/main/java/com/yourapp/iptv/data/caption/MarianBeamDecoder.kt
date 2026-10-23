package com.yourapp.iptv.data.caption

import ai.onnxruntime.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Four hypotheses share immutable encoder caches; unused native results close at every step. */
internal class MarianBeamDecoder(private val env: OrtEnvironment, private val config: MarianModelConfig,
    private val firstSession: () -> OrtSession, private val cachedSession: () -> OrtSession,
    private val run: (OrtSession, Map<String, OnnxTensorLike>) -> OrtSession.Result) {
    private data class Beam(val tokens: List<Long>, val score: Double, val cache: OrtSession.Result?) {
        val rank get() = score / tokens.size.coerceAtLeast(1)
    }
    suspend fun decode(mask: OnnxTensor, hidden: OnnxTensor): List<Long> {
        val owned = mutableSetOf<OrtSession.Result>()
        val empty = mutableMapOf<String, OnnxTensor>()
        var first: OrtSession.Result? = null
        var active = listOf(Beam(emptyList(), 0.0, null))
        val finished = mutableListOf<Beam>()
        try {
            for (step in 0 until 72) {
                currentCoroutineContext().ensureActive()
                val candidates = mutableListOf<Beam>()
                val session = if (step == 0) firstSession() else cachedSession()
                for (beam in active) {
                    currentCoroutineContext().ensureActive()
                    val token = beam.tokens.lastOrNull() ?: config.start
                    OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1)).use { ids ->
                    OnnxTensor.createTensor(env, booleanArrayOf(step > 0)).use { branch ->
                        val feed = mutableMapOf<String, OnnxTensorLike>("input_ids" to ids, "encoder_attention_mask" to mask)
                        if ("encoder_hidden_states" in session.inputNames) feed["encoder_hidden_states"] = hidden
                        if ("use_cache_branch" in session.inputNames) feed["use_cache_branch"] = branch
                        for (name in session.inputNames.filter { it.startsWith("past_key_values.") }) {
                            val owner = if (name.contains(".encoder.")) first else beam.cache
                            val output = name.replace("past_key_values.", "present.")
                            val value = owner?.get(output)?.orElse(null) as? OnnxTensor
                            feed[name] = value ?: empty.getOrPut(name) {
                                OnnxTensor.createTensor(env, FloatBuffer.allocate(0),
                                    longArrayOf(1, config.heads.toLong(), 0, (config.width / config.heads).toLong()))
                            }
                            check(step == 0 || value != null) { "Missing decoder cache" }
                        }
                        val result = run(session, feed)
                        owned += result
                        if (first == null) first = result
                        val logits = result.get("logits").orElseThrow { IllegalStateException("Missing decoder scores") } as OnnxTensor
                        for (choice in MarianBeamScoring.choices(logits.floatBuffer, config.vocabulary, config.pad, beam.tokens, 8)) {
                            val next = Beam(beam.tokens + choice.token, beam.score + choice.logProbability, result)
                            candidates += next
                        }
                    } }
                }
                val selected = mutableListOf<Beam>()
                for ((rank, candidate) in candidates.sortedByDescending { it.score }.take(8).withIndex()) {
                    if (candidate.tokens.last() == config.eos) {
                        if (rank < 4) finished += candidate.copy(cache = null)
                    } else selected += candidate
                    if (selected.size == 4) break
                }
                active = selected
                finished.sortByDescending { it.rank }
                while (finished.size > 4) finished.removeAt(finished.lastIndex)
                val keep = active.mapNotNull { it.cache }.toSet() + listOfNotNull(first)
                val stale = owned.filter { it !in keep }
                stale.forEach { it.close(); owned.remove(it) }
                if (active.isEmpty() || (finished.size == 4 && finished.last().rank >= active.maxOf { it.rank })) break
            }
            return (finished.maxByOrNull { it.rank } ?: active.maxByOrNull { it.rank })?.tokens.orEmpty()
        } finally {
            owned.forEach { it.close() }
            empty.values.forEach { it.close() }
        }
    }
}