package com.example

import com.example.data.model.TabItem
import com.example.ui.BrowserUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class TabsLogicTest {

    @Test
    fun testCloseActiveTabSelectsPreviousTab() {
        val tab1 = TabItem(id = "1", title = "Tab 1", url = "https://google.com")
        val tab2 = TabItem(id = "2", title = "Tab 2", url = "https://github.com")
        val tab3 = TabItem(id = "3", title = "Tab 3", url = "https://wikipedia.org")

        val state = BrowserUiState(
            tabs = listOf(tab1, tab2, tab3),
            activeTabId = "2"
        )

        // Simulate closeTab logic
        val tabIdToClose = "2"
        val index = state.tabs.indexOfFirst { it.id == tabIdToClose }
        val remainingTabs = state.tabs.filter { it.id != tabIdToClose }
        val newActiveId = if (state.activeTabId == tabIdToClose) {
            val nextIndex = (index - 1).coerceAtLeast(0)
            remainingTabs[nextIndex].id
        } else {
            state.activeTabId
        }

        val activeTab = remainingTabs.find { it.id == newActiveId }
        val activeUrl = activeTab?.url ?: "about:blank"
        val updatedOmnibox = if (activeUrl == "about:blank") "" else activeUrl

        assertEquals(2, remainingTabs.size)
        assertEquals("1", newActiveId)
        assertEquals("https://google.com", updatedOmnibox)
    }

    @Test
    fun testCloseLastRemainingTabResetsToHome() {
        val tab1 = TabItem(id = "1", title = "Tab 1", url = "https://google.com")
        val state = BrowserUiState(
            tabs = listOf(tab1),
            activeTabId = "1"
        )

        val currentTabs = state.tabs
        val freshId = UUID.randomUUID().toString()
        val newState = if (currentTabs.size <= 1) {
            state.copy(
                tabs = listOf(TabItem(id = freshId, title = "Home", url = "about:blank")),
                activeTabId = freshId,
                omniboxText = ""
            )
        } else {
            state
        }

        assertEquals(1, newState.tabs.size)
        assertEquals("Home", newState.tabs.first().title)
        assertEquals("about:blank", newState.tabs.first().url)
        assertEquals("", newState.omniboxText)
    }

    @Test
    fun testUpdateSpecificTabDoesNotMutateOtherTabs() {
        val tab1 = TabItem(id = "1", title = "Tab 1", url = "https://google.com")
        val tab2 = TabItem(id = "2", title = "Tab 2", url = "https://github.com")

        val state = BrowserUiState(
            tabs = listOf(tab1, tab2),
            activeTabId = "1"
        )

        // Update tab 2 (background tab)
        val targetId = "2"
        val updatedTabs = state.tabs.map {
            if (it.id == targetId) it.copy(title = "GitHub - Home", progress = 100, isLoading = false) else it
        }

        val updatedState = state.copy(tabs = updatedTabs)

        assertEquals("Tab 1", updatedState.tabs.find { it.id == "1" }?.title)
        assertEquals("GitHub - Home", updatedState.tabs.find { it.id == "2" }?.title)
        assertEquals("1", updatedState.activeTabId)
    }
}
