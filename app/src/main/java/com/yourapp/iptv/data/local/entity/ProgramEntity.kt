package com.yourapp.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey
    val id: String = "",
    val channelId: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String? = null
)