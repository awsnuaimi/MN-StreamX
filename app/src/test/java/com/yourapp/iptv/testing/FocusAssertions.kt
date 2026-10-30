package com.yourapp.iptv.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Lazy composition and dialog windows can settle on different frames. */
internal fun ComposeContentTestRule.waitForFocus(tag: String) {
    try {
        waitUntil(5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            it.config.getOrElse(SemanticsProperties.Focused) { false }
        } }
    } catch (error: Throwable) {
        println("Expected focus=$tag; actual=" + onAllNodes(isFocused()).fetchSemanticsNodes().map {
            it.config.getOrElse(SemanticsProperties.TestTag) { "no-tag" }
        })
        throw error
    }
    onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
}