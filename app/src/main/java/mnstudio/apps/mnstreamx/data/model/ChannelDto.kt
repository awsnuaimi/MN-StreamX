package mnstudio.apps.mnstreamx.data.model

data class ChannelDto(
    val id: String,
    val name: String,
    val logo: String? = null,
    val url: String,
    val group: String? = null
)