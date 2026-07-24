package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.cache.EpgCache
import com.yourapp.iptv.data.local.dao.EpgDao
import com.yourapp.iptv.data.mapper.toDomain
import com.yourapp.iptv.data.source.epg.EpgDataSource
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.domain.repository.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpgRepositoryImpl(
    private val epgDataSource: EpgDataSource,
    private val epgDao: EpgDao
) : EpgRepository {

    override suspend fun getPrograms(channelId: String): List<Program> = withContext(Dispatchers.IO) {
        // 1. التحقق من الـ Cache
        EpgCache.getPrograms(channelId)?.let { return@withContext it }

        // 2. جلب من قاعدة البيانات
        val entities = epgDao.getProgramsForChannel(channelId)
        if (entities.isNotEmpty()) {
            val programs = entities.map { 
                Program(it.title, it.startTime, it.endTime, it.description)
            }
            EpgCache.savePrograms(channelId, programs)
            return@withContext programs
        }

        // 3. إذا لم يوجد، نرجع قائمة فارغة
        emptyList()
    }

    // دالة مساعدة لتحميل EPG من رابط خارجي
    suspend fun loadEpgFromUrl(url: String): List<Program> = withContext(Dispatchers.IO) {
        val dtos = epgDataSource.fetchEpg(url)
        val programs = dtos.map { it.toDomain() }
        // حفظ في قاعدة البيانات
        val entities = programs.map { 
            com.yourapp.iptv.data.local.entity.ProgramEntity(
                channelId = it.title, // استخدام العنوان كمعرف مؤقت
                title = it.title,
                startTime = it.startTime,
                endTime = it.endTime,
                description = it.description
            )
        }
        epgDao.insertPrograms(entities)
        programs
    }
}