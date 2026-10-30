package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*

internal fun Modifier.homeFocusLinks(up: FocusRequester? = null, down: FocusRequester? = null,
    left: FocusRequester? = null, right: FocusRequester? = null): Modifier = focusProperties {
    this.up = up ?: FocusRequester.Cancel
    this.down = down ?: FocusRequester.Cancel
    this.left = left ?: FocusRequester.Cancel
    this.right = right ?: FocusRequester.Cancel
}

internal fun homeDirection(key: Key): HomeDpad? = when (key) {
    Key.DirectionUp -> HomeDpad.UP; Key.DirectionDown -> HomeDpad.DOWN
    Key.DirectionLeft -> HomeDpad.LEFT; Key.DirectionRight -> HomeDpad.RIGHT
    else -> null
}

/** Consume both halves of a routed arrow; OK, Back, digits and text editing are untouched. */
internal fun Modifier.homeDpadKeys(verticalOnly: Boolean = false, route: (HomeDpad) -> Unit): Modifier = onPreviewKeyEvent { event ->
    val direction = homeDirection(event.key)
    if (direction == null || (verticalOnly && direction != HomeDpad.UP && direction != HomeDpad.DOWN)) false else {
        if (event.type == KeyEventType.KeyDown && !event.nativeKeyEvent.isCanceled) route(direction)
        true
    }
}