package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import com.yourapp.iptv.ui.component.common.*

/** The safe default is Stay. Completing an asynchronous save cannot press an action. */
@Composable
internal fun SettingsExitPrompt(labels: SettingsLabels, saving: Boolean, error: Boolean,
    onSave: () -> Unit, onDiscard: () -> Unit, onStay: () -> Unit) {
    val focus = rememberContextFocus("settings-exit")
    AlertDialog(onDismissRequest = { if (!saving) onStay() }, modifier = focus.inputModifier(),
        title = { Text(labels.text("حفظ التغييرات؟", "Save changes?", "Änderungen speichern?")) },
        text = {
            focus.Resume(setOf("prompt"), "prompt", "stay")
            focus.Restore("prompt", listOf("save", "discard", "stay"), ready = !saving)
            Text(if (error) labels.text("تعذّر الحفظ. يمكنك إعادة المحاولة أو البقاء هنا.",
                "Saving failed. Retry or stay here.", "Speichern fehlgeschlagen. Erneut versuchen oder hier bleiben.")
            else labels.text("لديك تغييرات مظهر لم تُحفظ.", "Your appearance changes have not been saved.",
                "Deine Darstellungsänderungen sind noch nicht gespeichert."))
        },
        confirmButton = {
            TextButton(onSave, enabled = !saving, modifier = focus.target("prompt", "save", 0)
                .tvFocusIndicator().testTag("settings-exit-save")) { Text(labels.save()) }
        },
        dismissButton = { Row {
            TextButton(onDiscard, enabled = !saving, modifier = focus.target("prompt", "discard", 1)
                .tvFocusIndicator().testTag("settings-exit-discard")) { Text(labels.text("تجاهل", "Discard", "Verwerfen")) }
            TextButton(onStay, enabled = !saving, modifier = focus.target("prompt", "stay", 2)
                .tvFocusIndicator().testTag("settings-exit-stay")) { Text(labels.text("البقاء", "Stay", "Bleiben")) }
        } })
}