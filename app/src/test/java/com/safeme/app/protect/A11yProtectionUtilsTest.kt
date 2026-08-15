package com.safeme.app.protect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class A11yProtectionUtilsTest {

    // ---- componentEntriesMatch ----

    @Test
    fun exactMatchIsEqual() {
        assertTrue(
            A11yProtectionUtils.componentEntriesMatch(
                "com.app/com.app.Svc",
                "com.app/com.app.Svc",
            )
        )
    }

    @Test
    fun shortFormMatchesLongForm() {
        // `pkg/.svc` is the short form of `pkg/pkg.svc` in Settings storage.
        assertTrue(
            A11yProtectionUtils.componentEntriesMatch(
                "com.app/com.app.Svc",
                "com.app/.Svc",
            )
        )
    }

    @Test
    fun safemeOwnComponentShortFormMatchesLongForm() {
        // Regression: SafeMe's service is `com.safeme.app.service.SafeMeAccessibilityService`
        // (manifest `.service.SafeMeAccessibilityService`). The system persists the
        // enabled list in short form (`com.safeme.app/.service.SafeMeAccessibilityService`)
        // while Self-Healing writes the long form; state detection must treat both
        // as the same component or a running service reads as disabled.
        assertTrue(
            A11yProtectionUtils.componentEntriesMatch(
                "com.safeme.app/com.safeme.app.service.SafeMeAccessibilityService",
                "com.safeme.app/.service.SafeMeAccessibilityService",
            )
        )
    }

    @Test
    fun differentPackageDoesNotMatch() {
        assertFalse(
            A11yProtectionUtils.componentEntriesMatch(
                "com.app/com.app.Svc",
                "com.other/com.app.Svc",
            )
        )
    }

    @Test
    fun blankEntriesNeverMatch() {
        assertFalse(A11yProtectionUtils.componentEntriesMatch("", "com.app/com.app.Svc"))
        assertFalse(A11yProtectionUtils.componentEntriesMatch(null, "com.app/com.app.Svc"))
        assertFalse(A11yProtectionUtils.componentEntriesMatch("com.app/com.app.Svc", null))
    }

    // ---- canonicalAppendOnly ----

    @Test
    fun appendAddsMissingEntriesAndKeepsOrder() {
        val next = A11yProtectionUtils.canonicalAppendOnly(
            listOf("a/A", "b/B"),
            listOf("c/C"),
        )
        assertEquals(listOf("a/A", "b/B", "c/C"), next)
    }

    @Test
    fun duplicateAppendIsIgnored() {
        val next = A11yProtectionUtils.canonicalAppendOnly(
            listOf("a/A", "b/B"),
            listOf("b/B", "b/B"),
        )
        assertNull(next)
    }

    @Test
    fun shortFormAlreadyPresentNeedsNoWrite() {
        // "a/.A" equals "a/a.A" structurally, so appending it is a no-op:
        // null means no rewrite is needed (selfHealAll writes nothing).
        assertNull(
            A11yProtectionUtils.canonicalAppendOnly(
                listOf("a/a.A", "b/B"),
                listOf("a/.A"),
            )
        )
    }

    @Test
    fun duplicateFormsInCurrentAreCanonicalized() {
        // Both forms present in the current list — the duplicate is dropped
        // and flagged as a change so the stored list gets canonicalized.
        val next = A11yProtectionUtils.canonicalAppendOnly(
            listOf("a/a.A", "a/.A", "b/B"),
            listOf("c/C"),
        )
        assertEquals(listOf("a/a.A", "b/B", "c/C"), next)
    }

    @Test
    fun nothingToAppendReturnsNull() {
        assertNull(
            A11yProtectionUtils.canonicalAppendOnly(
                listOf("a/A", "b/B"),
                listOf("a/A", "b/B"),
            )
        )
    }

    @Test
    fun blankEntriesAreDropped() {
        val next = A11yProtectionUtils.canonicalAppendOnly(
            listOf("a/A", " ", ""),
            listOf(""),
        )
        assertEquals(listOf("a/A"), next)
    }

    @Test
    fun emptyCurrentWithAppendBuildsList() {
        assertEquals(
            listOf("a/A"),
            A11yProtectionUtils.canonicalAppendOnly(emptyList(), listOf("a/A")),
        )
    }

    @Test
    fun neverRemovesExistingEntries() {
        // Canonical ADD-ONLY: when the target is already present the function
        // returns null (no rewrite), so unselected services already in the
        // list are never touched — nothing gets removed.
        assertNull(
            A11yProtectionUtils.canonicalAppendOnly(
                listOf("other/OtherSvc", "a/A"),
                listOf("a/A"),
            )
        )
    }
}
