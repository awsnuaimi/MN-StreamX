package com.yourapp.iptv.domain.repository

import kotlinx.coroutines.channels.ReceiveChannel

/** Credentials are device-local secrets; callers must not log or persist the plaintext. */
class CloudCredentials(val key: String, val region: String) {
    override fun toString(): String = "CloudCredentials(region=$region, key=[REDACTED])"
    fun valid() = key.length in 10..300 && region.matches(Regex("[a-z][a-z0-9]{1,39}"))
}
interface CloudCredentialVault {
    suspend fun read(): CloudCredentials?
    suspend fun save(value: CloudCredentials)
    suspend fun clear()
}

sealed interface CloudCaptionEvent {
    data class Translation(val text: String) : CloudCaptionEvent
    data object Disconnected : CloudCaptionEvent
}
interface CloudCaptionSession : AutoCloseable {
    val events: ReceiveChannel<CloudCaptionEvent>
    suspend fun start()
    suspend fun write(samples: FloatArray)
    /** Fast, non-blocking request to end the session; close() owns native cleanup. */
    fun cancel()
    override fun close()
}
fun interface CloudCaptionSessionFactory {
    suspend fun open(credentials: CloudCredentials, source: String, target: String): CloudCaptionSession
}