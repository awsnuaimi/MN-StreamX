package com.yourapp.iptv.data.profile

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.gson.*
import com.yourapp.iptv.data.backup.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.profile.*

/** Stored form includes local rules; portable form deliberately excludes rules and the selected profile. */
internal object ProfileCodec {
    const val LIMIT = 2 * 1024 * 1024
    fun encode(value: UserProfiles): String {
        value.validate()
        return JsonObject().apply {
            addProperty("version",1);addProperty("active",value.activeId);addProperty("revision",value.revision)
            add("retired",JsonArray().apply { value.retired.sorted().forEach(::add) })
            add("profiles",rows(value.entries,portable = false))
        }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= LIMIT) }
    }
    fun decode(text: String?): UserProfiles {
        if (text == null) return UserProfiles()
        val root = BackupJson.read(text.toByteArray(Charsets.UTF_8),LIMIT)
        require(root.get("version").asInt == 1)
        val retired = root.getAsJsonArray("retired").also { require(it.size() <= 64) }.map { it.asString }.toSet()
        return UserProfiles(readRows(root.getAsJsonArray("profiles"),false),string(root,"active"),
            root.get("revision").asLong,retired).validate()
    }
    fun rows(profiles: List<UserProfile>, portable: Boolean = true) = JsonArray().apply {
        profiles.forEach { profile -> add(JsonObject().apply {
            addProperty("id",profile.id);addProperty("name",profile.name);addProperty("kind",profile.kind.name)
            addProperty("resume",profile.resumeAtStartup)
            profile.home?.let { value ->
                val prefs = mutablePreferencesOf();HomeAppearanceCodec.encode(prefs,value)
                add("home",BackupPreferenceCodec.encode(mapOf(BackupStore.HOME to BackupPreferenceCodec.values(prefs))).get("HOME"))
            }
            profile.player?.let { value ->
                val prefs = mutablePreferencesOf();PlayerAppearanceCodec.encode(prefs,value)
                add("player",BackupPreferenceCodec.encode(mapOf(BackupStore.PLAYER to BackupPreferenceCodec.values(prefs))).get("PLAYER"))
            }
            if (!portable) add("rules",encodeRules(profile.rules))
        }) }
    }
    fun readRows(array: JsonArray, portable: Boolean = true): List<UserProfile> {
        require(array.size() in 1..UserProfiles.LIMIT)
        val rows = array.map { element ->
            val row = element.asJsonObject;val id = string(row,"id")
            val kind = ProfileKind.valueOf(string(row,"kind"))
            fun prefs(key: String, area: BackupStore) = row.getAsJsonObject(key)?.let {
                require(it.size() <= 32)
                BackupPreferenceCodec.preferences(BackupPreferenceCodec.decode(JsonObject().apply { add(area.name,it) }).getValue(area))
            }
            UserProfile(id,string(row,"name"),kind,
                prefs("home",BackupStore.HOME)?.let { HomeAppearanceCodec.decode(it).copy(profileId = id) },
                prefs("player",BackupStore.PLAYER)?.let { PlayerAppearanceCodec.decode(it).copy(profileId = id) },
                row.get("resume").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean,
                if (portable) { if (kind == ProfileKind.CHILD) UserProfiles.childRules() else ParentalRules() }
                else decodeRules(row.getAsJsonObject("rules")))
        }
        UserProfiles(rows,UserProfiles.DEFAULT_ID).validate()
        return rows
    }
    private fun encodeRules(rules: ParentalRules) = JsonObject().apply {
        rules.validate()
        add("sections",JsonArray().apply { rules.sections.sortedBy { it.name }.forEach { add(it.name) } })
        add("groups",JsonObject().apply { rules.groups.toSortedMap().forEach { (key,names) ->
            add(key,JsonArray().apply { names.sorted().forEach(::add) })
        } })
    }
    private fun decodeRules(row: JsonObject): ParentalRules {
        val sections = row.getAsJsonArray("sections").also { require(it.size() <= 4) }.map { ParentalSection.valueOf(it.asString) }.toSet()
        val groups = row.getAsJsonObject("groups").also { require(it.size() <= 16) }.entrySet().associate { (key,value) ->
            key to value.asJsonArray.also { require(it.size() <= 512) }.map { it.asString }.toSet()
        }
        return ParentalRules(sections,groups).validate()
    }
    private fun string(row: JsonObject, key: String) = BackupPreferenceCodec.text(row,key)
}