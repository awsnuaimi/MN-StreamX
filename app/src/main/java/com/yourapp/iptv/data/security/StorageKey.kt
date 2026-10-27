package com.yourapp.iptv.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yourapp.iptv.domain.security.SecureStorageException
import com.yourapp.iptv.domain.security.SecureStorageFailure
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal interface StorageKey {
    /** Creation is permitted only for a caller-confirmed absent encrypted record. */
    fun get(createIfMissing: Boolean): SecretKey
}

internal class ProvidedStorageKey(private val key: () -> SecretKey) : StorageKey {
    override fun get(createIfMissing: Boolean) = key()
}

internal interface StorageKeyBackend {
    fun load(alias: String): SecretKey?
    fun create(alias: String): SecretKey
}

internal class AndroidStorageKey(private val alias: String,
    private val backend: StorageKeyBackend = DeviceStorageKeyBackend) : StorageKey {
    override fun get(createIfMissing: Boolean): SecretKey = secureStorage {
        synchronized(lock) {
            backend.load(alias) ?: if (createIfMissing) backend.create(alias)
            else throw SecureStorageException(SecureStorageFailure.KEY_MISSING)
        }
    }
    private companion object {
        // Serializes all instances, including backup and repository instances for the same alias.
        val lock = Any()
    }
}

private object DeviceStorageKeyBackend : StorageKeyBackend {
    override fun load(alias: String): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(alias, null) ?: return null
        return key as? SecretKey ?: throw ProviderException("Unexpected encrypted storage key type")
    }
    override fun create(alias: String): SecretKey = KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        generateKey()
    }
}