package com.yourapp.iptv.data.profile

import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class ProfileCodecTest {
    @Test fun namesRulesAppearanceAndSelectionSurviveEncryptedReopening() {
        val row = profileRow().copy(name = "أسرة",home = HomeAppearance(language = HomeLanguage.GERMAN,theme = HomeTheme.RED,profileId = PROFILE_A),
            rules = ParentalRules(setOf(ParentalSection.MOVIES),mapOf("a".repeat(64) to setOf("Private"))))
        val value = UserProfiles(UserProfiles().entries + row,PROFILE_A,revision = 7)
        val cipher = DeviceProfileCipher { SecretKeySpec(ByteArray(32) { 2 },"AES") }
        val first = cipher.encrypt(ProfileCodec.encode(value));val second = cipher.encrypt(ProfileCodec.encode(value))
        assertNotEquals(first,second);assertFalse(first.contains("Private"))
        assertEquals(value,ProfileCodec.decode(cipher.decrypt(first)))
        assertTrue(runCatching { cipher.decrypt(first.dropLast(4) + "AAAA") }.isFailure)
        assertTrue(runCatching { DeviceProfileCipher { SecretKeySpec(ByteArray(32) { 3 },"AES") }.decrypt(first) }.isFailure)
    }
    @Test fun portableMetadataCannotTransferThePinGrantOrChildRules() {
        val row = profileRow(kind = ProfileKind.CHILD).copy(rules = ParentalRules(emptySet()))
        val encoded = ProfileCodec.rows(UserProfiles().entries + row)
        assertFalse(encoded.toString().contains("rules"));assertFalse(encoded.toString().contains("active"))
        val decoded = ProfileCodec.readRows(encoded).last()
        assertEquals(ParentalSection.entries.toSet(),decoded.rules.sections)
    }
    @Test fun scopesAreUnambiguousAndDefaultKeysRemainByteForByteCompatible() {
        val source = AppSettings(m3uUrl = "https://synthetic.invalid/a")
        val raw = PlaylistCacheKey.of(source);assertEquals(raw,ProfileScope.of(source))
        val personal = ProfileScope.of(source.copy(profileId = PROFILE_A))
        assertEquals(PROFILE_A,ProfileScope.profile(personal));assertEquals(raw,ProfileScope.source(personal))
        assertTrue(ProfileScope.valid(personal));assertFalse(ProfileScope.valid("p1:default:$raw"))
        assertFalse(ProfileScope.valid("p1:../:$raw"));assertFalse(ProfileScope.valid(personal + ":suffix"))
    }
    @Test fun duplicateIdsAndInvalidActiveProfileCannotBePersisted() {
        assertTrue(runCatching { ProfileCodec.encode(UserProfiles(UserProfiles().entries + profileRow() + profileRow())) }.isFailure)
        assertTrue(runCatching { ProfileCodec.encode(UserProfiles(activeId = PROFILE_A)) }.isFailure)
    }
}