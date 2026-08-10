package com.safeme.app.protect

import com.safeme.app.data.LockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the JVM-testable App Lock security core. */
class AppLockManagerTest {

    // ------------------------------------------------------------ input rules

    @Test
    fun pin_accepts4to6Digits() {
        assertTrue(AppLockManager.isValidInput(LockType.PIN, "1234"))
        assertTrue(AppLockManager.isValidInput(LockType.PIN, "123456"))
        assertFalse(AppLockManager.isValidInput(LockType.PIN, "123"))
        assertFalse(AppLockManager.isValidInput(LockType.PIN, "1234567"))
        assertFalse(AppLockManager.isValidInput(LockType.PIN, "12a4"))
        assertFalse(AppLockManager.isValidInput(LockType.PIN, ""))
    }

    @Test
    fun password_requiresAtLeast4Chars() {
        assertTrue(AppLockManager.isValidInput(LockType.PASSWORD, "abcd"))
        assertTrue(AppLockManager.isValidInput(LockType.PASSWORD, "a1!Z"))
        assertFalse(AppLockManager.isValidInput(LockType.PASSWORD, "abc"))
        assertFalse(AppLockManager.isValidInput(LockType.PASSWORD, ""))
    }

    @Test
    fun pattern_requiresAtLeast4Dots() {
        assertTrue(AppLockManager.isValidInput(LockType.PATTERN, "1-2-3-4"))
        assertTrue(AppLockManager.isValidInput(LockType.PATTERN, "1-2-3-4-5-6-7-8-9"))
        assertFalse(AppLockManager.isValidInput(LockType.PATTERN, "1-2-3"))
        assertFalse(AppLockManager.isValidInput(LockType.PATTERN, "1-2-3-"))
        assertFalse(AppLockManager.isValidInput(LockType.PATTERN, "a-b-c-d"))
    }

    @Test
    fun off_neverValid() {
        assertFalse(AppLockManager.isValidInput(LockType.OFF, "1234"))
        assertFalse(AppLockManager.isValidInput(LockType.OFF, ""))
    }

    // ---------------------------------------------------------------- crypto

    @Test
    fun hash_isDeterministicForSameSalt() {
        val salt = AppLockManager.generateSalt()
        assertEquals(AppLockManager.hash("1234", salt), AppLockManager.hash("1234", salt))
    }

    @Test
    fun hash_differsAcrossSaltsAndInputs() {
        val saltA = AppLockManager.generateSalt()
        val saltB = AppLockManager.generateSalt()
        assertNotEquals(AppLockManager.hash("1234", saltA), AppLockManager.hash("1234", saltB))
        assertNotEquals(AppLockManager.hash("1234", saltA), AppLockManager.hash("1235", saltA))
    }

    @Test
    fun saltAndHashAreHexOfExpectedLengths() {
        val salt = AppLockManager.generateSalt()
        assertEquals(32, salt.length) // 16 bytes
        assertTrue(salt.matches(Regex("^[0-9a-f]+$")))
        val digest = AppLockManager.hash("a-password!", salt)
        assertEquals(64, digest.length) // 256-bit key
        assertTrue(digest.matches(Regex("^[0-9a-f]+$")))
    }

    @Test
    fun constantTimeEquals_matchesDigests() {
        val salt = AppLockManager.generateSalt()
        val a = AppLockManager.hash("password", salt)
        val b = AppLockManager.hash("password", salt)
        val c = AppLockManager.hash("passwore", salt)
        assertTrue(AppLockManager.constantTimeEquals(a, b))
        assertFalse(AppLockManager.constantTimeEquals(a, c))
        assertFalse(AppLockManager.constantTimeEquals(a, ""))
    }

    @Test
    fun constantTimeEquals_rejectsLengthMismatchAndGarbage() {
        assertFalse(AppLockManager.constantTimeEquals("abc", "abcd"))
        assertFalse(AppLockManager.constantTimeEquals("zz", "zz")) // garbage hex → false
        assertFalse(AppLockManager.constantTimeEquals("0f", "0g"))
    }

    // ------------------------------------------------------------- backoff

    @Test
    fun firstFourFailuresAreFree() {
        for (attempts in 1..4) {
            assertEquals(0L, AppLockManager.lockoutDeadlineFor(attempts, 1000L))
        }
    }

    @Test
    fun failures5to9_getExponentialBackoff() {
        assertEquals(1000L + 1000L, AppLockManager.lockoutDeadlineFor(5, 1000L))
        assertEquals(1000L + 2000L, AppLockManager.lockoutDeadlineFor(6, 1000L))
        assertEquals(1000L + 4000L, AppLockManager.lockoutDeadlineFor(7, 1000L))
        assertEquals(1000L + 8000L, AppLockManager.lockoutDeadlineFor(8, 1000L))
        assertEquals(1000L + 16000L, AppLockManager.lockoutDeadlineFor(9, 1000L))
    }

    @Test
    fun tenthFailureLocksOutFor5Minutes() {
        assertEquals(1000L + 5 * 60 * 1000L, AppLockManager.lockoutDeadlineFor(10, 1000L))
        // Sustained failures keep the 5-min lockout (no longer backoff).
        assertEquals(1000L + 5 * 60 * 1000L, AppLockManager.lockoutDeadlineFor(15, 1000L))
    }
}
