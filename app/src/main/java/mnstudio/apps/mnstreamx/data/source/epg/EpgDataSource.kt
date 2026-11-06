package mnstudio.apps.mnstreamx.data.source.epg

import mnstudio.apps.mnstreamx.data.model.EpgXmltvDto
import mnstudio.apps.mnstreamx.data.network.HttpClient
import okhttp3.Request

class EpgDataSource {
    private val client = HttpClient.create()
    private val parser = EpgParser()

    suspend fun fetchEpg(url: String): List<EpgXmltvDto> {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        
        return if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            parser.parse(body)
        } else {
            emptyList()
        }
    }
}