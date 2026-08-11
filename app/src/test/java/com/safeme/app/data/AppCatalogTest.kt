package com.safeme.app.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(AppCategory.SHOPPING, cat("com.doordash"))
        assertEquals(AppCategory.PAYMENT, cat("com.paypal.android.p2pmobile"))
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
        assertEquals(AppCategory.PAYMENT, cat("com.random.pkg", "Cash App"))
        assertEquals(AppCategory.PAYMENT, cat("com.random.pkg", "Chase Bank"))
        assertEquals(AppCategory.PAYMENT, cat("com.random.pkg", "Google Wallet"))
        assertEquals(AppCategory.SHOPPING, cat("com.random.pkg", "Uber Eats"))
        assertEquals(AppCategory.SHOPPING, cat("com.random.pkg", "Instacart"))
    }

    @Test
    fun paymentAndShoppingPrefixesWinOverGenericPrefixes() {
        // Money apps stay in Payment (never Shopping), and order/delivery apps
        // stay in Shopping (never Payment or Other).
        assertEquals(AppCategory.PAYMENT, cat("com.paypal.android.p2pmobile", "PayPal"))
        assertEquals(AppCategory.PAYMENT, cat("com.squareup.cash", "Cash App"))
        assertEquals(AppCategory.PAYMENT, cat("com.revolut.revolut", "Revolut"))
        assertEquals(AppCategory.SHOPPING, cat("com.ubercab.eats", "Uber Eats"))
        assertEquals(AppCategory.SHOPPING, cat("com.fedex", "FedEx"))
        assertEquals(AppCategory.SHOPPING, cat("com.instacart", "Instacart"))
    }

    @Test
    fun paymentAppsAreClassified() {
        // Banking, wallets and fintech must all land in Payment.
        val payments = listOf(
            "com.chase.sig.android" to "Chase Mobile",
            "com.infonow.bofa" to "Bank of America",
            "com.wf.wellsfargomobile" to "Wells Fargo",
            "com.citi.citimobile" to "Citi Mobile",
            "com.konyl.capitalone" to "Capital One",
            "com.usbank" to "U.S. Bank",
            "com.tdbank" to "TD Bank",
            "mobile.chimebank" to "Chime",
            "com.sofi.app" to "SoFi",
            "com.n26.android" to "N26",
            "com.klarna.android" to "Klarna",
            "com.affirm.android" to "Affirm",
            "com.zellepay.zelle" to "Zelle",
            "com.robinhood.android" to "Robinhood",
            "com.coinbase.android" to "Coinbase",
            "com.eg.android.AlipayGphone" to "Alipay",
            "com.samsung.android.spay" to "Samsung Wallet",
            "com.google.android.apps.walletnfcrel" to "Google Wallet",
            "net.one97.paytm" to "Paytm",
            "com.phonepe.app" to "PhonePe",
            "transferwise" to "Wise",
            "com.monzo" to "Monzo",
            "co.banking.android" to "Starling Bank",
            "com.starlingbank.android" to "Starling",
            "com.remitly" to "Remitly",
            "com.payoneer.android" to "Payoneer",
        )
        payments.forEach { (pkg, label) ->
            assertEquals("$pkg should be Payment", AppCategory.PAYMENT, cat(pkg, label))
        }
        // Obscure banks are caught by the label keyword fallback.
        assertEquals(AppCategory.PAYMENT, cat("com.unknown.bank.app", "MyLocal Bank"))
        assertEquals(AppCategory.PAYMENT, cat("com.obscure.app", "Digital Wallet"))
        assertEquals(AppCategory.PAYMENT, cat("com.obscure.app", "Instant Payment"))
    }

    @Test
    fun shoppingAppsAreClassified() {
        // Marketplaces, stores and delivery apps must all land in Shopping.
        val shopping = listOf(
            "com.amazon.mshop" to "Amazon Shopping",
            "com.alibaba.aliexpresshd" to "AliExpress",
            "com.shopee" to "Shopee",
            "com.lazada.android" to "Lazada",
            "com.myntra.android" to "Myntra",
            "com.nykaa" to "Nykaa",
            "com.meesho.supply" to "Meesho",
            "com.target.ui" to "Target",
            "com.bestbuy.android" to "Best Buy",
            "com.thehomedepot" to "The Home Depot",
            "com.costco" to "Costco",
            "com.kroger" to "Kroger",
            "com.ebay.mobile" to "eBay",
            "com.etsy" to "Etsy",
            "com.einnovation.temu" to "TEMU",
            "com.walmart" to "Walmart",
            "com.wayfair" to "Wayfair",
            "com.swiggy" to "Swiggy",
            "com.zomato" to "Zomato",
            "com.doordash" to "DoorDash",
            "com.ubercab.eats" to "Uber Eats",
            "com.instacart" to "Instacart",
            "com.fedex" to "FedEx",
            "com.ups.mobile.android" to "UPS",
            "com.dhl" to "DHL",
            "com.usps" to "USPS",
        )
        shopping.forEach { (pkg, label) ->
            assertEquals("$pkg should be Shopping", AppCategory.SHOPPING, cat(pkg, label))
        }
        // Generic labels fall back to keywords without being over-broad.
        assertEquals(AppCategory.SHOPPING, cat("com.obscure.store.app", "Online Shopping"))
        assertEquals(AppCategory.SHOPPING, cat("com.obscure.market.app", "Fresh Market"))
        assertEquals(AppCategory.SHOPPING, cat("com.obscure.food.app", "Grocery Delivery"))
    }

    @Test
    fun unrelatedAppsStayOutOfPaymentAndShopping() {
        // No false positives: media, tools and games must not be miscategorized
        // as Payment or Shopping.
        val label = "Photoshop"
        assertNotEquals(AppCategory.SHOPPING, cat("com.adobe.psmobile", label))
        assertNotEquals(AppCategory.PAYMENT, cat("com.overkillsoftware.payday2", "Payday 2"))
        assertNotEquals(AppCategory.SHOPPING, cat("com.android.vending", "Google Play Store"))
        assertEquals(AppCategory.VIDEO_MUSIC, cat("com.google.android.youtube", "YouTube"))
        assertEquals(AppCategory.MESSAGING, cat("com.whatsapp", "WhatsApp"))
        assertEquals(AppCategory.NEWS_PROD, cat("com.microsoft.office", "Microsoft 365"))
        assertEquals(AppCategory.OTHER, cat("com.obscure.startup.app", "Something else"))
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
                AppCategory.PAYMENT,
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
