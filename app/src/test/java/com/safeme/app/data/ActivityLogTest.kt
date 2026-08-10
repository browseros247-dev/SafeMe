package com.safeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the Home feed / History activity log. */
class ActivityLogTest {

    @Test
    fun append_prependsNewestFirst() {
        val first = ActivityEntry(ACTIVITY_VPN, "VPN active", "sub", 1000L)
        val second = ActivityEntry(ACTIVITY_BLOCK, "Blocked Chrome", "sub", 2000L)
        val result = appendActivity(emptyList(), first)
        val result2 = appendActivity(result, second)
        assertEquals(listOf(second, first), result2)
    }

    @Test
    fun append_dedupesConsecutiveSameTypeAndTitle() {
        val entry = ActivityEntry(ACTIVITY_VPN, "VPN active", "sub", 1000L)
        val once = appendActivity(emptyList(), entry)
        val twice = appendActivity(once, entry.copy(timeMillis = 2000L))
        assertEquals(1, twice.size)
    }

    @Test
    fun append_allowsSameTitleAfterDifferentEntry() {
        val a = ActivityEntry(ACTIVITY_BLOCK, "Blocked Chrome", "s", 1000L)
        val b = ActivityEntry(ACTIVITY_VPN, "VPN active", "s", 2000L)
        val c = ActivityEntry(ACTIVITY_BLOCK, "Blocked Chrome", "s", 3000L)
        val r1 = appendActivity(emptyList(), a)
        val r2 = appendActivity(r1, b)
        val r3 = appendActivity(r2, c)
        assertEquals(listOf(c, b, a), r3)
    }

    @Test
    fun append_capsAtLimit() {
        var list: List<ActivityEntry> = emptyList()
        for (i in 1..(ACTIVITY_LOG_CAP + 10)) {
            list = appendActivity(
                list,
                ActivityEntry(ACTIVITY_BLOCK, "Blocked app $i", "s", i.toLong())
            )
        }
        assertEquals(ACTIVITY_LOG_CAP, list.size)
        // Newest (highest number) survived.
        assertEquals("Blocked app ${ACTIVITY_LOG_CAP + 10}", list.first().title)
    }

    @Test
    fun jsonRoundTripPreservesEntries() {
        val entries = listOf(
            ActivityEntry(ACTIVITY_BLOCK, "Blocked Chrome", "Launch blocked by schedule", 1234L),
            ActivityEntry(ACTIVITY_VPN, "VPN stopped", "Internet filtering is off", 5678L),
        )
        val parsed = activityFromJson(activityToJson(entries))
        assertEquals(entries, parsed)
    }

    @Test
    fun jsonEmptyOrGarbageYieldsEmptyList() {
        assertEquals(emptyList<ActivityEntry>(), activityFromJson(null))
        assertEquals(emptyList<ActivityEntry>(), activityFromJson("not json"))
        assertEquals(emptyList<ActivityEntry>(), activityFromJson(""))
    }
}
