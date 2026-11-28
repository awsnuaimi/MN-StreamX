package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.CaptionSettings

interface PolishCaptionRunner {
    /** Returns true if this user action changes the gate from paused to enabled. */
    fun arm(): Boolean
    suspend fun run(input: CaptionAudioInput, settings: CaptionSettings, publish: (CaptionPipelineState) -> Unit)
}