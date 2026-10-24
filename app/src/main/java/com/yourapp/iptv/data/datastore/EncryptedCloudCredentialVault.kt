package com.yourapp.iptv.data.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.yourapp.iptv.domain.repository.CloudCredentials
import com.yourapp.iptv.domain.repository.CloudCredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per-install Keystore encryption; the prefs file is excluded from cloud/device backups. */
internal class EncryptedCloudCredentialVault(context: Context) : CloudCredentialVault {
    private val prefs = context.applicationContext.getSharedPreferences("caption_cloud_secrets", Context.MODE_PRIVATE)
    private val alias = "mnstreamx_caption_cloud_v1"
    private val name = "azure_key_gcm"
    private val lock = Any()

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    override suspend fun read(): CloudCredentials? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val stored = prefs.getString(name, null) ?: return@synchronized null
            try {
                val bytes = Base64.decode(stored, Base64.NO_WRAP)
                require(bytes.size > 12) { "Corrupt encrypted credential" }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                }
                val obj = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
                CloudCredentials(obj.getString("key"), obj.getString("region")).takeIf { it.valid() }
            } catch (_: Exception) {
                // Do not log key material, ciphertext or SDK-provided exception details.
                null
            }
        }
    }

    override suspend fun save(value: CloudCredentials) = withContext(Dispatchers.IO) {
        require(value.valid()) { "Invalid cloud configuration" }
        synchronized(lock) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
            val plaintext = JSONObject().put("key", value.key).put("region", value.region).toString()
                .toByteArray(Charsets.UTF_8)
            val record = cipher.iv + cipher.doFinal(plaintext)
            check(prefs.edit().putString(name, Base64.encodeToString(record, Base64.NO_WRAP)).commit())
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) { check(prefs.edit().remove(name).commit()) }
    }
}