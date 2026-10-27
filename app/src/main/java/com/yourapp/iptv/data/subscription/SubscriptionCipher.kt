package com.yourapp.iptv.data.subscription

import com.yourapp.iptv.data.security.*
import javax.crypto.SecretKey

internal interface SubscriptionCipher {
    fun encrypt(value: String): String
    fun encryptNew(value: String): String = encrypt(value)
    fun decrypt(value: String): String
}

/** New keys are allowed only for the initial migration/write, never while reading an account. */
internal class KeystoreSubscriptionCipher(keys: StorageKey = AndroidStorageKey("mnstreamx_subscriptions_v1")) : SubscriptionCipher {
    constructor(key: () -> SecretKey) : this(ProvidedStorageKey(key))
    private val cipher = TextRecordCipher(keys, "MN-StreamX/subscriptions/v1", MAX_PLAIN, MAX_RECORD)
    override fun encrypt(value: String) = cipher.encrypt(value, newRecord = false)
    override fun encryptNew(value: String) = cipher.encrypt(value, newRecord = true)
    override fun decrypt(value: String) = cipher.decrypt(value)
    companion object {
        const val MAX_PLAIN = 512 * 1024
        const val MAX_RECORD = 720 * 1024
    }
}