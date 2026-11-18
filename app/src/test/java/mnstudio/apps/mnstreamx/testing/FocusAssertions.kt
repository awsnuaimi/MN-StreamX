package mnstudio.apps.mnstreamx.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

private const val REENTRANT_LAYOUT = "performMeasureAndLayout called during measure layout"
private const val MAX_TRANSIENT_LAYOUT_RETRIES = 3

/**
 * Wait for the requested TV focus, including lazy items inside Android Dialog windows.
 *
 * Robolectric can occasionally drive a Dialog's traversal while Compose is already
 * measuring. Retry only that exact, transient framework exception. Incorrect focus,
 * missing nodes, and repeated layout exceptions must still fail the test.
 */
internal fun ComposeContentTestRule.waitForFocus(tag: String) {
    var transientLayoutRetries = 0
    try {
        waitUntil(5_000) {
            try {
                onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
                    node.config.getOrElse(SemanticsProperties.Focused) { false }
                }
            } catch (error: IllegalArgumentException) {
                if (error.message?.contains(REENTRANT_LAYOUT) != true ||
                    ++transientLayoutRetries > MAX_TRANSIENT_LAYOUT_RETRIES
                ) {
                    throw error
                }
                false
            }
        }
    } catch (error: Throwable) {
        val actual = runCatching {
            onAllNodes(isFocused()).fetchSemanticsNodes().map { node ->
                node.config.getOrElse(SemanticsProperties.TestTag) { "no-tag" }
            }
        }.getOrElse { listOf("focus unavailable: ${it.javaClass.simpleName}") }
        println("Expected focus=$tag; actual=$actual; transient layout retries=$transientLayoutRetries")
        throw error
    }
    onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
}