package com.yourapp.iptv.data.backup

import com.google.gson.*
import com.yourapp.iptv.data.profile.ProfileCodec
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.data.subscription.SubscriptionCodec
import com.yourapp.iptv.domain.model.*

internal object BackupPayloadCodec {
    fun encode(value: BackupPayload): ByteArray {
        BackupSizeBudget.payload(value)
        validate(value)
        return JsonObject().apply {
            addProperty("application", "MN-StreamX"); addProperty("version", 2); addProperty("created", value.createdAt)
            add("subscriptions", JsonParser.parseString(SubscriptionCodec.encode(value.subscriptions)))
            add("preferences", BackupPreferenceCodec.encode(value.areas))
            add("favorites", favorites(value.favorites))
            add("profiles",ProfileCodec.rows(value.profiles))
        }.toString().toByteArray(Charsets.UTF_8).also {
            if (it.size > BackupLimits.PLAIN) throw BackupException(BackupProblem.TOO_LARGE)
        }
    }
    fun decode(bytes: ByteArray): BackupPayload = try {
        val root = BackupJson.read(bytes, BackupLimits.PLAIN)
        require(BackupPreferenceCodec.text(root, "application") == "MN-StreamX")
        val version = root.get("version").asInt
        if (version !in 1..2) throw BackupException(BackupProblem.UNSUPPORTED)
        BackupPayload(root.get("created").asLong,
            SubscriptionCodec.decode(root.getAsJsonObject("subscriptions").toString()),
            BackupPreferenceCodec.decode(root.getAsJsonObject("preferences")), readFavorites(root.getAsJsonArray("favorites")),
            if (version == 1) UserProfiles().entries else ProfileCodec.readRows(root.getAsJsonArray("profiles")))
            .also(::validate)
    } catch (error: BackupException) { throw error }
    catch (_: Exception) { throw BackupException(BackupProblem.INVALID) }

    private fun validate(value: BackupPayload) {
        require(value.createdAt >= 0)
        SubscriptionCodec.decode(SubscriptionCodec.encode(value.subscriptions))
        val accounts = value.subscriptions.entries.filter { it.settings.hasSelectedSource() }
        require(accounts.map { PlaylistCacheKey.of(it.settings) }.distinct().size == accounts.size)
        UserProfiles(value.profiles).validate()
        PortablePreferences.validate(value.areas, value.subscriptions,value.profiles)
        val sources = PortablePreferences.sourceKeys(value.subscriptions,value.profiles)
        require(value.favorites.size <= BackupLimits.FAVORITES && value.favorites.distinct().size == value.favorites.size)
        require(value.favorites.all { it.source in sources && it.channel.isNotBlank() && it.channel.length <= 512 })
    }
    fun favorites(rows: List<BackupFavorite>) = JsonArray().apply { rows.forEach { row -> add(JsonObject().apply {
        addProperty("source", row.source); addProperty("channel", row.channel)
    }) } }
    fun readFavorites(array: JsonArray): List<BackupFavorite> {
        require(array.size() <= 100_000)
        return array.map { element ->
            val row = element.asJsonObject
            BackupFavorite(BackupPreferenceCodec.text(row, "source"), BackupPreferenceCodec.text(row, "channel"))
                .also { require(ProfileScope.valid(it.source) && it.channel.isNotBlank() && it.channel.length <= 512) }
        }
    }
}