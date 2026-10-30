@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yourapp.iptv.ui.component.common

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*

/** Scroll containers supply their own focus target; an extra group would hide the real child. */
internal fun Modifier.rememberedFocusGroup(
    entry: FocusRequester,
    fallback: FocusRequester? = null,
    scrollable: Boolean = false,
    onExit: (FocusDirection) -> FocusRequester = { FocusRequester.Default }
): Modifier = focusRequester(entry).onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown &&
        (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)) {
        entry.saveFocusedChild()
    }
    false
}.focusProperties {
    enter = { direction ->
        // Explicit child requests (dialogs, accessibility, touch) keep their intended target.
        if (direction == FocusDirection.Enter) FocusRequester.Default
        else if (entry.restoreFocusedChild()) FocusRequester.Cancel else fallback ?: FocusRequester.Default
    }
    exit = { direction ->
        entry.saveFocusedChild()
        onExit(direction)
    }
}.then(if (scrollable) Modifier else Modifier.focusGroup())

/** Programmatic region entry opts into memory; direct requests to individual controls do not. */
internal fun FocusRequester.restoreOrRequestFocus(fallback: FocusRequester? = null) {
    if (!restoreFocusedChild()) (fallback ?: this).requestFocus()
}