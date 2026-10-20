package com.yourapp.iptv.domain.organization

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Frozen v17/v18 identity values. Never calculate expected hashes with production code. */
class IdentityLockTest {
    @Test fun originalNormalizationRemainsByteForByteCompatible() {
        val frozen = mapOf(
            "SYR" to "syr", "AR | SYRIA HD" to "ar syria hd",
            "PL Ogólnotematyczne" to "pl ogolnotematyczne",
            "PL | Kanały FHD" to "pl kanały fhd", "PL Łódź" to "pl łodz",
            "AR | قَنَاة سُورِيَّة HD" to "ar قناه سوريه hd",
            "FR Canal+ Movies" to "fr canal movies", "  \t| : " to ""
        )
        frozen.forEach { (input, expected) ->
            assertEquals(input, expected, OrganizationNames.normalize(input))
            assertEquals(input, expected, OrganizationNames.normalizeIdentity(input))
        }
        assertEquals("kanały", OrganizationNames.normalize("Kanały"))
        assertEquals("kanaly", OrganizationNames.normalizeMatch("Kanały"))
    }

    @Test fun frozenCanonicalGroupsAndPersistedKeysDoNotChange() {
        val pairs = mapOf(
            "SYR" to ("syr" to "7a44c28229c2f0b104079a02e9648edd"),
            "AR SYRIA HD" to ("ar syria" to "e5dbd3ba9edde1d4fa222167bc16fb74"),
            "PL Ogólnotematyczne" to ("pl ogolnotematyczne" to "fc13e05ffc887ce5083fc2a5c2a8ecf5"),
            "PL Kanały HD" to ("pl kanały" to "c7a6f34966877c6177801ed608a9330b")
        )
        pairs.forEach { (input, expected) ->
            assertEquals(input, expected.first, OrganizationNames.canonicalGroup(input))
            assertEquals(input, expected.second, OrganizationNames.groupKey(input))
        }
        assertEquals(OrganizationNames.groupKey("AR SYRIA HD"), OrganizationNames.groupKey("AR SYRIA FHD HD"))
        assertEquals(OrganizationNames.groupKey("PL Kanały HD"), OrganizationNames.groupKey("PL Kanały FHD"))
        assertEquals("~ungrouped", OrganizationNames.canonicalGroup(null))
    }

    @Test fun identityAndGroupKeyIgnoreDeviceLocale() {
        val old = Locale.getDefault()
        try {
            for (tag in listOf("ar-EG", "tr-TR", "de-DE", "pl-PL")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals("pl kanały", OrganizationNames.canonicalGroup("PL Kanały FHD"))
                assertEquals("c7a6f34966877c6177801ed608a9330b", OrganizationNames.groupKey("PL Kanały FHD"))
            }
        } finally { Locale.setDefault(old) }
    }
}