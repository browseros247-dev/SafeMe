package com.safeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the JSONC → strict-JSON preprocessing used by backup import. */
class JsoncTest {

    @Test
    fun lineCommentsAreStripped() {
        val raw = """
            {
              // this is a comment
              "a": 1, // trailing comment
            }
        """.trimIndent()
        assertEquals("{\n  \n  \"a\": 1, \n}", Jsonc.toStrictJson(raw))
    }

    @Test
    fun blockCommentsAreStripped() {
        val raw = """
            {
              /* block
                 comment */
              "a": 1
            }
        """.trimIndent()
        val cleaned = Jsonc.toStrictJson(raw)
        assertEquals("{\n  \n  \"a\": 1\n}", cleaned)
    }

    @Test
    fun trailingCommasAreStripped() {
        assertEquals("{\"a\": 1}", Jsonc.toStrictJson("{\"a\": 1,}"))
        assertEquals("{\"a\": [1, 2]}", Jsonc.toStrictJson("{\"a\": [1, 2,]}"))
        assertEquals("{\"a\": 1, \"b\": 2}", Jsonc.toStrictJson("{\"a\": 1, \"b\": 2,}"))
    }

    @Test
    fun commentsInsideStringsAreKept() {
        // A keyword or value may legitimately contain "//" or "/*".
        val raw = """{"value": "https://example.com//path", "note": "a /* not a comment */"}"""
        assertEquals(raw, Jsonc.toStrictJson(raw))
    }

    @Test
    fun trailingCommaInsideStringIsKept() {
        val raw = """{"value": "a,b,", "list": [1, 2,]}"""
        assertEquals("""{"value": "a,b,", "list": [1, 2]}""", Jsonc.toStrictJson(raw))
    }

    @Test
    fun escapedQuotesInsideStringsDoNotBreakParsing() {
        val raw = """{"value": "say \"hi\" // not a comment"}"""
        assertEquals(raw, Jsonc.toStrictJson(raw))
    }

    @Test
    fun commentMarkersInsideEscapedStringsSurvive() {
        val raw = """{"value": "C:\\temp\\//x"}"""
        assertEquals(raw, Jsonc.toStrictJson(raw))
    }

    @Test
    fun commentsAndTrailingCommasTogether() {
        val raw = """
            // header comment
            {
              "schedules": [
                {"id": "1", "name": "Study", "days": [0, 2,],},
              ],
            }
        """.trimIndent()
        val cleaned = Jsonc.toStrictJson(raw)
        assertEquals(
            "\n{\n  \"schedules\": [\n    {\"id\": \"1\", \"name\": \"Study\", \"days\": [0, 2]}\n  ]\n}",
            cleaned,
        )
    }

    @Test
    fun unclosedBlockCommentProducesStrippedOutput() {
        // The stripper never throws; the JSON parser reports the failure later.
        val cleaned = Jsonc.toStrictJson("{\"a\": 1} /* never closed")
        assertEquals("{\"a\": 1} ", cleaned)
    }
}
