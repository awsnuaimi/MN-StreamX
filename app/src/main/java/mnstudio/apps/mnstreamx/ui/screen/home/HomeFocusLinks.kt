@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
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

internal fun homeRegionExit(direction: FocusDirection, above: FocusRequester? = null,
    below: FocusRequester? = null, sidebar: FocusRequester? = null, sidebarOnRight: Boolean = false,
    rememberAbove: Boolean = false, rememberBelow: Boolean = false, spatialBelow: Boolean = false,
    fallbackBelow: FocusRequester? = null): FocusRequester {
    val target = when {
        direction == FocusDirection.Up -> above
        direction == FocusDirection.Down -> below
        direction == (if (sidebarOnRight) FocusDirection.Right else FocusDirection.Left) -> sidebar
        direction == FocusDirection.Left || direction == FocusDirection.Right -> null
        else -> return FocusRequester.Default
    } ?: return FocusRequester.Cancel
    if (direction == FocusDirection.Down && spatialBelow) return FocusRequester.Default
    val restore = (direction == FocusDirection.Up && rememberAbove) ||
        (direction == FocusDirection.Down && rememberBelow)
    if (restore && target.restoreFocusedChild()) return FocusRequester.Cancel
    return if (direction == FocusDirection.Down) fallbackBelow ?: target else target
}

internal fun Modifier.homeDpadKeys(verticalOnly: Boolean = false, route: (HomeDpad) -> Unit): Modifier = onPreviewKeyEvent { event ->
    val direction = homeDirection(event.key)
    if (direction == null || (verticalOnly && direction != HomeDpad.UP && direction != HomeDpad.DOWN)) false else {
        if (event.type == KeyEventType.KeyDown && !event.nativeKeyEvent.isCanceled) route(direction)
        true
    }
}