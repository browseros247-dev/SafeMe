package com.safeme.app.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the shared App Picker catalog: classification + grouping. */
class AppCatalogTest {

    private fun cat(pkg: String, label: String = pkg, system: Int = ApplicationInfo.CATEGORY_UNDEFINED) =
        AppCatalog.categorize(pkg, label, system)

    // ---------- Classification ----------

    @Test
    fun knownPackagesClassifyCorrectly() {
        assertEquals(AppCategory.SOCIAL, cat("com.instagram.android"))
        assertEquals(AppCategory.VIDEO_MUSIC, cat("com.spotify.music"))
        assertEquals(AppCategory.MESSAGING, cat("com.whatsapp"))
        assertEquals(AppCategory.SHOPPING, cat("com.amazon.mshop.android.shopping"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.paypal.android.p2pmobile"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.doordash"))
        assertEquals(AppCategory.GAMES, cat("com.supercell.clashofclans"))
        assertEquals(AppCategory.NEWS_PROD, cat("com.android.chrome"))
    }

    @Test
    fun mostSpecificPrefixWins() {
        // Messenger must land in Messaging, not under the generic com.facebook prefix.
        assertEquals(AppCategory.MESSAGING, cat("com.facebook.orca"))
        assertEquals(AppCategory.SOCIAL, cat("com.facebook.katana"))
    }

    @Test
    fun curatedRulesWinOverCoarseSystemCategory() {
        // Play reports communication apps as CATEGORY_SOCIAL; the curated rules
        // must keep WhatsApp/Messages in Messaging (prototype taxonomy).
        assertEquals(AppCategory.MESSAGING, cat("com.whatsapp", system = ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(AppCategory.MESSAGING, cat("com.google.android.apps.messaging", system = ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(AppCategory.VIDEO_MUSIC, cat("com.spotify.music", system = ApplicationInfo.CATEGORY_SOCIAL))
    }

    @Test
    fun systemCategoryClassifiesUnknownApps() {
        // For apps we don't curate, the system flag is the fallback signal.
        assertEquals(AppCategory.GAMES, cat("com.unknown.studio.game", system = ApplicationInfo.CATEGORY_GAME))
        assertEquals(AppCategory.VIDEO_MUSIC, cat("com.unknown.app", system = ApplicationInfo.CATEGORY_VIDEO))
        assertEquals(AppCategory.SOCIAL, cat("com.unknown.app", system = ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(AppCategory.NEWS_PROD, cat("com.unknown.app", system = ApplicationInfo.CATEGORY_PRODUCTIVITY))
    }

    @Test
    fun unknownPackageFallsBackToOther() {
        assertEquals(AppCategory.OTHER, cat("com.obscure.startup.app"))
        assertEquals(AppCategory.OTHER, cat(""))
    }

    @Test
    fun labelKeywordsClassifyObfuscatedPackages() {
        assertEquals(AppCategory.SOCIAL, cat("com.zhiliaoapp.something", "TikTok"))
        assertEquals(AppCategory.GAMES, cat("com.random.pkg", "Clash of Clans"))
        assertEquals(AppCategory.NEWS_PROD, cat("com.random.pkg", "Gmail"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.random.pkg", "Cash App"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.random.pkg", "Uber Eats"))
    }

    @Test
    fun shippingPaymentPrefixesWinOverGenericPrefixes() {
        // PayPal must stay in Shipping & Payment, not fall under the broader
        // label/social heuristics, and delivery apps must not land in Shopping.
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.paypal.android.p2pmobile", "PayPal"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.ubercab.eats", "Uber Eats"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.fedex", "FedEx"))
        assertEquals(AppCategory.SHIPPING_PAYMENT, cat("com.squareup.cash", "Cash App"))
    }

    @Test
    fun labelKeywordDoesNotOverridePackageRule() {
        // Package rules win over label keywords even if the label suggests elsewhere.
        assertEquals(AppCategory.GAMES, cat("com.mojang.minecraftpe", "Not a game"))
    }

    @Test
    fun categoryOrderMatchesPrototype() {
        assertEquals(
            listOf(
                AppCategory.SOCIAL,
                AppCategory.VIDEO_MUSIC,
                AppCategory.MESSAGING,
                AppCategory.SHOPPING,
                AppCategory.SHIPPING_PAYMENT,
                AppCategory.GAMES,
                AppCategory.NEWS_PROD,
                AppCategory.OTHER,
            ),
            AppCatalog.CATEGORY_ORDER,
        )
    }

    // ---------- Grouping ----------

    private fun app(pkg: String, label: String, category: AppCategory) =
        InstalledApp(pkg, label, category)

    @Test
    fun groupAppsPreservesFixedOrderAndSortsWithinGroups() {
        val apps = listOf(
            app("com.zzz", "Zed", AppCategory.GAMES),
            app("com.aaa", "Alpha", AppCategory.SOCIAL),
            app("com.bbb", "Beta", AppCategory.GAMES),
            app("com.ccc", "Gamma", AppCategory.SOCIAL),
        )
        val groups = AppCatalog.groupApps(apps)
        assertEquals(AppCategory.SOCIAL, groups[0].first)
        assertEquals(listOf("Alpha", "Gamma"), groups[0].second.map { it.label })
        assertEquals(AppCategory.GAMES, groups[1].first)
        assertEquals(listOf("Beta", "Zed"), groups[1].second.map { it.label })
        assertEquals(2, groups.size)
    }

    @Test
    fun groupAppsSkipsEmptyCategories() {
        val apps = listOf(app("com.x", "X", AppCategory.OTHER))
        val groups = AppCatalog.groupApps(apps)
        assertEquals(listOf(AppCategory.OTHER), groups.map { it.first })
    }

    @Test
    fun groupAppsSearchFiltersWithinGroupsAndHidesEmpty() {
        val apps = listOf(
            app("com.instagram.android", "Instagram", AppCategory.SOCIAL),
            app("com.twitter.android", "Twitter", AppCategory.SOCIAL),
            app("com.spotify.music", "Spotify", AppCategory.VIDEO_MUSIC),
        )
        val groups = AppCatalog.groupApps(apps, "insta")
        assertEquals(1, groups.size)
        assertEquals(AppCategory.SOCIAL, groups[0].first)
        assertEquals(listOf("Instagram"), groups[0].second.map { it.label })
        // Package names are searched too.
        val byPkg = AppCatalog.groupApps(apps, "spotify")
        assertEquals(AppCategory.VIDEO_MUSIC, byPkg[0].first)
        // No match → empty list (caller shows the empty state).
        assertTrue(AppCatalog.groupApps(apps, "zzz").isEmpty())
    }

    @Test
    fun groupAppsEmptyListIsEmpty() {
        assertTrue(AppCatalog.groupApps(emptyList()).isEmpty())
    }
}
