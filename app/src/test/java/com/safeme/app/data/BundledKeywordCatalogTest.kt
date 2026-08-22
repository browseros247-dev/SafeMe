package com.safeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledKeywordCatalogTest {

    @Test
    fun allLanguagesArePresentAndNonEmpty() {
        assertTrue(BundledKeywordCatalog.blockKeywordsByLanguage.isNotEmpty())
        assertEquals(37, BundledKeywordCatalog.blockKeywordsByLanguage.size)
        BundledKeywordCatalog.blockKeywordsByLanguage.forEach { (lang, entries) ->
            assertTrue("language $lang has no entries", entries.isNotEmpty())
        }
    }

    @Test
    fun entriesHaveNoBlanksOrDuplicatesWithinALanguage() {
        BundledKeywordCatalog.blockKeywordsByLanguage.forEach { (lang, entries) ->
            entries.forEach { entry ->
                assertTrue("blank entry in $lang", entry.isNotBlank())
            }
            assertEquals("duplicate entries in $lang", entries.size, entries.distinct().size)
        }
    }

    @Test
    fun verbatimPayloadCounts() {
        val sizes = BundledKeywordCatalog.blockKeywordsByLanguage.mapValues { it.value.size }
        assertEquals(532, sizes["en"])
        assertEquals(132, sizes["es"])
        assertEquals(133, sizes["pt"])
        assertEquals(23, sizes["ru"])
        assertEquals(8, sizes["de"])
        assertEquals(1189, sizes.values.sum())
    }

    @Test
    fun fallbackUsesEnglishForNullBlankAndUnknownLanguages() {
        val en = BundledKeywordCatalog.blockKeywordsByLanguage.getValue("en")
        assertEquals(en, BundledKeywordCatalog.blockKeywordsFor(null))
        assertEquals(en, BundledKeywordCatalog.blockKeywordsFor(""))
        assertEquals(en, BundledKeywordCatalog.blockKeywordsFor("  "))
        assertEquals(en, BundledKeywordCatalog.blockKeywordsFor("xx"))
    }

    @Test
    fun selectedLanguageIsReturnedVerbatim() {
        val de = BundledKeywordCatalog.blockKeywordsByLanguage.getValue("de")
        assertEquals(de, BundledKeywordCatalog.blockKeywordsFor("de"))
        assertEquals(de, BundledKeywordCatalog.blockKeywordsFor("DE"))
    }

    @Test
    fun spotChecksFromReferencePayload() {
        val en = BundledKeywordCatalog.blockKeywordsFor("en")
        assertTrue(en.contains("kaufmich.com"))
        assertTrue(en.contains("porn"))

        val de = BundledKeywordCatalog.blockKeywordsFor("de")
        assertTrue(de.contains("wichsen"))
        assertTrue(de.contains("erotik"))
    }

    @Test
    fun recoveryWhitelistSeedMatchesReferencePayload() {
        val seed = BundledKeywordCatalog.recoveryWhitelistSeed
        assertEquals(20, seed.size)
        assertTrue(seed.none { it.isBlank() })
        listOf(
            "reddit.com/r/nofap",
            "yourbrainonporn.com",
            "quitporn",
            "stopwatchingporn",
            "quittingporn",
            "overcomeporn",
            "stopporn",
            "pornblock",
            "blockporn",
            "foodporn",
            "stopmasturbat",
            "quitmasturbat",
            "xxxtentacion",
            "escapingporn",
            "escapingmasturbat",
        ).forEach { term ->
            assertTrue("seed missing $term", term in seed)
        }
    }

    @Test
    fun domainClassification() {
        assertTrue(BundledKeywordCatalog.isDomainEntry("pornhub.com"))
        assertTrue(BundledKeywordCatalog.isDomainEntry("kaufmich.com"))
        assertFalse(BundledKeywordCatalog.isDomainEntry("porn"))
        assertFalse(BundledKeywordCatalog.isDomainEntry("oral sex"))
    }

    @Test
    fun domainsForKeywordsSplitMatchesClassification() {
        val en = BundledKeywordCatalog.blockKeywordsFor("en")
        val domains = BundledKeywordCatalog.domainsFor("en")
        val keywords = BundledKeywordCatalog.keywordsFor("en")
        assertEquals(en.size, domains.size + keywords.size)
        assertTrue(domains.contains("kaufmich.com"))
        assertFalse(domains.contains("porn"))
        assertTrue(keywords.contains("porn"))
        // English fallback flows through domainsFor too.
        assertEquals(domains, BundledKeywordCatalog.domainsFor(null))
    }
}
