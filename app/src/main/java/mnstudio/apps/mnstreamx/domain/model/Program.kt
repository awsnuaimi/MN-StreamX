package mnstudio.apps.mnstreamx.domain.model

data class Program(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val description: String? = null
)