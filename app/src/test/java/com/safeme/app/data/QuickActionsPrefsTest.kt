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
            QuickActionType.KEYWORD,
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
        // "focus" is a legacy id from before the Focus tab removal — it must be
        // dropped exactly like any other unknown id.
        assertEquals(
            listOf(QuickActionType.VPN),
            quickActionsFromJson("[\"focus\",\"bogus\",\"vpn\"]")
        )
    }

    @Test
    fun garbageJson_fallsBackToDefaults() {
        assertEquals(DEFAULT_QUICK_ACTIONS, quickActionsFromJson("not json"))
    }

    @Test
    fun allTypesRoundTrip() {
        val all = QuickActionType.entries
        assertEquals(all, quickActionsFromJson(quickActionsToJson(all)))
    }
}
