package com.example

import com.example.data.model.SuggestionItem
import com.example.data.model.SuggestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SuggestionTest {

    @Test
    fun testSuggestionItemCreation() {
        val directUrlItem = SuggestionItem(
            title = "github.com",
            destinationUrl = "https://github.com",
            type = SuggestionType.DIRECT_URL,
            subtitle = "Buka Langsung"
        )

        assertEquals("github.com", directUrlItem.title)
        assertEquals("https://github.com", directUrlItem.destinationUrl)
        assertEquals(SuggestionType.DIRECT_URL, directUrlItem.type)
        assertEquals("Buka Langsung", directUrlItem.subtitle)

        val queryItem = SuggestionItem(
            title = "resep masakan",
            destinationUrl = "resep masakan",
            type = SuggestionType.QUERY,
            subtitle = "Pencarian Web"
        )

        assertEquals(SuggestionType.QUERY, queryItem.type)
        assertNotNull(queryItem.title)
    }
}
