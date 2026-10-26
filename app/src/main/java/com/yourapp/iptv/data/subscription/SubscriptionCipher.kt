package com.yourapp.iptv.data.subscription

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SubscriptionCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

/** A fresh random GCM IV for every write, with a per-install Android Keystore key. */
internal class KeystoreSubscriptionCipher(private val key: () -> SecretKey = ::subscriptionSecret) : SubscriptionCipher {
    private val aad = "MN-StreamX/subscriptions/v1".toByteArray(Charsets.UTF_8)
    override fun encrypt(value: String): String {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_PLAIN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
        return "gcm1:" + Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    override fun decrypt(value: String): String {
        require(value.startsWith("gcm1:") && value.length <= MAX_RECORD)
        val record = Base64.getDecoder().decode(value.removePrefix("gcm1:"))
        require(record.size in 28..MAX_PLAIN + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, record.copyOfRange(0, 12))); updateAAD(aad)
        }
        return String(cipher.doFinal(record.copyOfRange(12, record.size)), Charsets.UTF_8)
    }
    companion object {
        const val MAX_PLAIN = 512 * 1024
        const val MAX_RECORD = 720 * 1024
    }
}

private val subscriptionKeyLock = Any()
private fun subscriptionSecret(): SecretKey = synchronized(subscriptionKeyLock) {
    val alias = "mnstreamx_subscriptions_v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
    }.generateKey()
}