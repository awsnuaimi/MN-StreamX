package com.yourapp.iptv.data.profile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ProfileCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

internal class DeviceProfileCipher(private val key: () -> SecretKey = ::profileKey) : ProfileCipher {
    private val aad = "MN-StreamX/profiles/v1".toByteArray(Charsets.UTF_8)
    override fun encrypt(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8);require(bytes.size <= ProfileCodec.LIMIT)
        return try { Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,key());updateAAD(aad)
            "gcm1:" + Base64.getEncoder().encodeToString(iv + doFinal(bytes))
        } } finally { bytes.fill(0) }
    }
    override fun decrypt(value: String): String {
        require(value.startsWith("gcm1:") && value.length <= 3 * ProfileCodec.LIMIT)
        val record = Base64.getDecoder().decode(value.removePrefix("gcm1:"))
        require(record.size in 28..ProfileCodec.LIMIT + 28)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,record.copyOfRange(0,12)));updateAAD(aad)
            doFinal(record,12,record.size - 12)
        }
        return try { String(plain,Charsets.UTF_8) } finally { plain.fill(0) }
    }
}
private fun profileKey(): SecretKey = synchronized(DeviceProfileCipher::class.java) {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val alias = "mnstreamx_profiles_v1"
    (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
    }.generateKey()
}