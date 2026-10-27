package com.yourapp.iptv.data.profile

import com.yourapp.iptv.data.security.*
import javax.crypto.SecretKey

internal interface ProfileCipher {
    fun encrypt(value: String): String
    fun encryptNew(value: String): String = encrypt(value)
    fun decrypt(value: String): String
}

internal class DeviceProfileCipher(keys: StorageKey = AndroidStorageKey("mnstreamx_profiles_v1")) : ProfileCipher {
    constructor(key: () -> SecretKey) : this(ProvidedStorageKey(key))
    private val cipher = TextRecordCipher(keys, "MN-StreamX/profiles/v1", ProfileCodec.LIMIT, 3 * ProfileCodec.LIMIT)
    override fun encrypt(value: String) = cipher.encrypt(value, newRecord = false)
    override fun encryptNew(value: String) = cipher.encrypt(value, newRecord = true)
    override fun decrypt(value: String) = cipher.decrypt(value)
}