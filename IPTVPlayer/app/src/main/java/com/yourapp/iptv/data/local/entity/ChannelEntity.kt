package com.yourapp.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val group: String? = null
)