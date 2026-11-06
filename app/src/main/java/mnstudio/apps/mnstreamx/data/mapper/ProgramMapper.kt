package mnstudio.apps.mnstreamx.data.mapper

import mnstudio.apps.mnstreamx.data.model.EpgXmltvDto
import mnstudio.apps.mnstreamx.domain.model.Program
import java.text.SimpleDateFormat
import java.util.Locale

fun EpgXmltvDto.toDomain(): Program {
    // تحويل النص الزمني إلى Long (بسيط حالياً)
    val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
    val startTime = try { format.parse(this.start)?.time ?: 0L } catch (e: Exception) { 0L }
    val endTime = try { format.parse(this.stop)?.time ?: 0L } catch (e: Exception) { 0L }

    return Program(
        title = this.title,
        startTime = startTime,
        endTime = endTime,
        description = this.desc
    )
}