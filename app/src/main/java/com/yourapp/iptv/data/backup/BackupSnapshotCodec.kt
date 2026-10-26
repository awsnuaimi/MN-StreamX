package com.yourapp.iptv.data.backup

import com.google.gson.*

internal object BackupSnapshotCodec {
    fun encode(value: BackupSnapshot): ByteArray {
        BackupSizeBudget.snapshot(value)
        return JsonObject().apply {
            addProperty("version", 1)
            add("stores", BackupPreferenceCodec.encode(value.stores.mapValues { BackupPreferenceCodec.values(it.value) }))
            add("favorites", BackupPayloadCodec.favorites(value.favorites))
            add("legacy", JsonArray().apply { value.legacyFavorites.forEach(::add) })
        }.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupLimits.JOURNAL)
            decode(it)
        }
    }

    fun decode(bytes: ByteArray): BackupSnapshot {
        val root = BackupJson.read(bytes, BackupLimits.JOURNAL)
        require(root.get("version").asInt == 1)
        val stores = BackupPreferenceCodec.decode(root.getAsJsonObject("stores"))
        require(stores.keys == BackupStore.entries.toSet())
        val legacy = root.getAsJsonArray("legacy").also { require(it.size() <= 100_000) }.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            it.asString.also { id -> require(id.isNotBlank() && id.length <= 512) }
        }
        return BackupSnapshot(stores.mapValues { BackupPreferenceCodec.preferences(it.value) },
            BackupPayloadCodec.readFavorites(root.getAsJsonArray("favorites")), legacy)
    }
}