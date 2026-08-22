package com.safeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistSeedTest {

    @Test
    fun freshInstallSeedsWhitelistAndPersists() {
        val (list, shouldPersist) = resolveWhitelistSeed(emptyList(), alreadySeeded = false)
        assertEquals(BundledKeywordCatalog.recoveryWhitelistSeed, list)
        assertTrue(shouldPersist)
    }

    @Test
    fun alreadySeededNeverReSeedsEvenWhenEmpty() {
        val (list, shouldPersist) = resolveWhitelistSeed(emptyList(), alreadySeeded = true)
        assertTrue(list.isEmpty())
        assertFalse(shouldPersist)
    }

    @Test
    fun userEditedListAlwaysWins() {
        val stored = listOf("mysite.example", "keepme")
        val (unseeded, persistA) = resolveWhitelistSeed(stored, alreadySeeded = false)
        val (reseeded, persistB) = resolveWhitelistSeed(stored, alreadySeeded = true)
        assertEquals(stored, unseeded)
        assertFalse(persistA)
        assertEquals(stored, reseeded)
        assertFalse(persistB)
    }

    @Test
    fun seedIsNotDuplicatedAfterPersisting() {
        // After the seed was persisted and the flag set, reads are stable no-ops.
        val seeded = BundledKeywordCatalog.recoveryWhitelistSeed
        val (list, shouldPersist) = resolveWhitelistSeed(seeded, alreadySeeded = true)
        assertEquals(seeded, list)
        assertFalse(shouldPersist)
    }
}
