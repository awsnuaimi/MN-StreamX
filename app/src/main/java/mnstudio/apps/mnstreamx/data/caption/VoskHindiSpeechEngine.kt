package mnstudio.apps.mnstreamx.data.caption

import java.io.File
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine

internal class VoskHindiSpeechEngine(folder: File) : SpeechEngine by VoskSpeechEngine(folder, "hi")