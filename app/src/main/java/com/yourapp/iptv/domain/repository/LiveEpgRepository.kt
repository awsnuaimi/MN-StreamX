package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgSchedule

interface LiveEpgRepository {
    /** Null means this channel does not belong to the selected Xtream account. */
    suspend fun load(channel: Channel, settings: AppSettings): EpgSchedule?
}