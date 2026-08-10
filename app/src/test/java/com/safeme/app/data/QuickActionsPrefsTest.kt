package com.safeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the Home Quick Actions codec. */
class QuickActionsPrefsTest {

    @Test
    fun nullOrBlank_returnsDefaults() {
        assertEquals(DEFAULT_QUICK_ACTIONS, quickActionsFromJson(null))
        assertEquals(DEFAULT_QUICK_ACTIONS, quickActionsFromJson(""))
        assertEquals(DEFAULT_QUICK_ACTIONS, quickActionsFromJson("  "))
    }

    @Test
    fun jsonRoundTripPreservesOrder() {
        val actions = listOf(
            QuickActionType.FOCUS,
            QuickActionType.VPN,
            QuickActionType.HISTORY,
            QuickActionType.BACKUP,
        )
        assertEquals(actions, quickActionsFromJson(quickActionsToJson(actions)))
    }

    @Test
    fun emptyArray_staysEmpty() {
        assertEquals(emptyList<QuickActionType>(), quickActionsFromJson("[]"))
    }

    @Test
    fun unknownIdsAreDroppedWithoutCrash() {
        assertEquals(
            listOf(QuickActionType.FOCUS, QuickActionType.VPN),
            quickActionsFromJson("[\"focus\",\"bogus\",\"vpn\"]")
        )
    }

    @Test
    fun garbageJson_fallsBackToDefaults() {
        assertEquals(DEFAULT_QUICK_ACTIONS, quickActionsFromJson("not json"))
    }

    @Test
    fun allEightTypesRoundTrip() {
        val all = QuickActionType.entries
        assertEquals(all, quickActionsFromJson(quickActionsToJson(all)))
    }
}
