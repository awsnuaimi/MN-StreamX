package com.yourapp.iptv.data.datastore

import com.google.gson.JsonParser
import com.yourapp.iptv.domain.organization.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OrganizationCodecCompatibilityTest {
    @Test fun previousConfigurationsWithoutSortingLoadWithTheirExistingRules() {
        val original = OrganizationConfig(sourceKey = "source", enabled = true, revision = 8,
            channelOverrides = mapOf("channel" to "arabic-syria"))
        val legacy = JsonParser.parseString(OrganizationCodec.encode(original)).asJsonObject.apply { remove("folderSort") }
        assertEquals(original, OrganizationCodec.decode(legacy.toString(), "source"))
    }
    @Test fun sortModeRoundTripsAndUnknownValuesAreRejectedWithoutLosingTheSavedConfiguration() {
        val original = OrganizationConfig(sourceKey = "source", folderSort = FolderSort.COUNT)
        val encoded = OrganizationCodec.encode(original)
        assertEquals(original, OrganizationCodec.decode(encoded, "source"))
        assertThrows(IOException::class.java) { OrganizationCodec.decode(encoded.replace("COUNT", "INVALID"), "source") }
    }
}