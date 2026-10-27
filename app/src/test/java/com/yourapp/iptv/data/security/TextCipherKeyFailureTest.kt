package com.yourapp.iptv.data.security

import com.yourapp.iptv.data.profile.DeviceProfileCipher
import com.yourapp.iptv.data.subscription.KeystoreSubscriptionCipher
import com.yourapp.iptv.domain.security.SecureStorageFailure
import java.util.Base64
import javax.crypto.Cipher
import org.junit.Assert.*
import org.junit.Test

class TextCipherKeyFailureTest {
    private class Area(val backend: FaultKeyBackend, val encrypt: (String) -> String,
        val first: (String) -> String, val decrypt: (String) -> String, val aad: String)
    private fun areas(): List<Area> {
        val profiles = FaultKeyBackend(); val p = DeviceProfileCipher(profiles.access())
        val accounts = FaultKeyBackend(); val s = KeystoreSubscriptionCipher(accounts.access())
        return listOf(Area(profiles,p::encrypt,p::encryptNew,p::decrypt,"MN-StreamX/profiles/v1"),
            Area(accounts,s::encrypt,s::encryptNew,s::decrypt,"MN-StreamX/subscriptions/v1"))
    }
    @Test fun legacyCiphertextAndAadRemainReadableWithoutRewritingOrGeneratingKeys() {
        for (area in areas()) {
            val record = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE,area.backend.value);updateAAD(area.aad.toByteArray(Charsets.UTF_8))
                "gcm1:" + Base64.getEncoder().encodeToString(iv + doFinal("legacy".toByteArray()))
            }
            assertEquals("legacy",area.decrypt(record));assertEquals(0,area.backend.creates)
        }
    }
    @Test fun missingKeyOnReadOrUpdateCannotCreateAReplacement() {
        for (area in areas()) {
            val record = area.encrypt("preserve")
            area.backend.value = null
            expectStorageFailure(SecureStorageFailure.KEY_MISSING) { area.decrypt(record) }
            expectStorageFailure(SecureStorageFailure.KEY_MISSING) { area.encrypt("replacement") }
            assertEquals(0,area.backend.creates)
        }
    }
    @Test fun firstWriteExplicitlyCreatesAKeyAndRoundTrips() {
        for (area in areas()) {
            area.backend.value = null
            val record = area.first("initial")
            assertEquals("initial",area.decrypt(record));assertEquals(1,area.backend.creates)
        }
    }
    @Test fun wrongKeyAndTamperedTagBothRemainAuthenticationFailures() {
        for (area in areas()) {
            val record = area.encrypt("preserve")
            val bytes = Base64.getDecoder().decode(record.removePrefix("gcm1:"))
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            expectStorageFailure(SecureStorageFailure.AUTHENTICATION_FAILED) {
                area.decrypt("gcm1:" + Base64.getEncoder().encodeToString(bytes))
            }
            area.backend.value = testStorageKey(99)
            expectStorageFailure(SecureStorageFailure.AUTHENTICATION_FAILED) { area.decrypt(record) }
            assertEquals(0,area.backend.creates)
        }
    }
    @Test fun malformedInputIsRejectedBeforeLookingUpAKey() {
        for (area in areas()) {
            for (record in listOf("bad", "gcm1:!", "gcm1:AA==")) {
                expectStorageFailure(SecureStorageFailure.INVALID_RECORD) { area.decrypt(record) }
            }
            assertEquals(0,area.backend.reads);assertEquals(0,area.backend.creates)
        }
    }
    @Test fun temporaryReadFailureCanRecoverUsingExactlyTheOriginalCiphertextAndKey() {
        for (area in areas()) {
            val record = area.encrypt("preserve")
            area.backend.failure = java.io.IOException("Synthetic outage")
            expectStorageFailure(SecureStorageFailure.TEMPORARILY_UNAVAILABLE) { area.decrypt(record) }
            area.backend.failure = null
            assertEquals("preserve",area.decrypt(record));assertEquals(0,area.backend.creates)
        }
    }
}