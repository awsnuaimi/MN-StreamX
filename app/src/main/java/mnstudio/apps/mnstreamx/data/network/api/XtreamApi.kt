package mnstudio.apps.mnstreamx.data.network.api

import mnstudio.apps.mnstreamx.data.model.XtreamChannelDto
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApi {
    @GET("get_live_streams")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: String? = null
    ): List<XtreamChannelDto>
}