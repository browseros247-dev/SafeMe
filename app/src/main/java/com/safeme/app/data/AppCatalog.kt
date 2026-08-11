package com.safeme.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.safeme.app.R

/**
 * Category taxonomy for installed apps, in fixed display order. Every App
 * Picker renders categories in exactly this order (see [AppCatalog.CATEGORY_ORDER]),
 * matching the prototype's canonical list — Social, Video & Music, Messaging,
 * Shopping, Payment, Games, News & Productivity — with an [OTHER] fallback for
 * apps that can't be classified. Shopping covers e-commerce/marketplaces and
 * order/delivery apps; Payment covers banking, wallets and money apps.
 */
enum class AppCategory(val labelRes: Int) {
    SOCIAL(R.string.app_cat_social),
    VIDEO_MUSIC(R.string.app_cat_video_music),
    MESSAGING(R.string.app_cat_messaging),
    SHOPPING(R.string.app_cat_shopping),
    PAYMENT(R.string.app_cat_payment),
    GAMES(R.string.app_cat_games),
    NEWS_PROD(R.string.app_cat_news_prod),
    OTHER(R.string.app_cat_other),
}

/** One launchable app discovered on the device, pre-classified. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
)

/**
 * Single source of truth for the App Picker. Apps are always discovered
 * dynamically from PackageManager (nothing is hard-coded about *which* apps
 * appear); only the category rules for apps Android leaves unclassified are
 * static, and they live here — one place, unit-testable.
 */
object AppCatalog {

    /** Fixed display order — the prototype's categories, [OTHER] last. */
    val CATEGORY_ORDER: List<AppCategory> = AppCategory.entries

    /**
     * Discovers launchable apps (ACTION_MAIN + CATEGORY_LAUNCHER), dedupes,
     * excludes SafeMe itself and classifies each app. Slow PackageManager
     * work — call off the main thread.
     */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        val ownPackage = context.packageName
        val seen = HashSet<String>()
        val result = mutableListOf<InstalledApp>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == ownPackage) continue
            if (!seen.add(pkg)) continue
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val label = ai?.loadLabel(pm)?.toString() ?: pkg
            val category = categorize(pkg, label, ai?.category ?: ApplicationInfo.CATEGORY_UNDEFINED)
            result.add(InstalledApp(pkg, label, category))
        }
        return result
    }

    /**
     * Pure grouping used by every picker: categories in [CATEGORY_ORDER], apps
     * alphabetical within each group, empty groups dropped. An optional search
     * query filters inside each group (empty groups hidden) — exactly the
     * prototype's search-then-group behavior.
     */
    fun groupApps(
        apps: List<InstalledApp>,
        query: String = "",
    ): List<Pair<AppCategory, List<InstalledApp>>> {
        val q = query.trim().lowercase()
        val groups = mutableListOf<Pair<AppCategory, List<InstalledApp>>>()
        for (category in CATEGORY_ORDER) {
            val matched = apps
                .asSequence()
                .filter { it.category == category }
                .filter {
                    q.isEmpty() ||
                        it.label.lowercase().contains(q) ||
                        it.packageName.lowercase().contains(q)
                }
                .sortedBy { it.label.lowercase() }
                .toList()
            if (matched.isNotEmpty()) groups.add(category to matched)
        }
        return groups
    }

    /**
     * Pure classifier — unit-testable without Android.
     *
     * Precedence:
     *  1. Package-name prefix rules (most specific prefix wins). These match
     *     the prototype's taxonomy exactly — e.g. WhatsApp/Messages are
     *     Messaging even though Play reports their coarse category as Social.
     *  2. Android's built-in [ApplicationInfo.category] when it's set — the
     *     fallback that catches the long tail (unknown games → Games, media →
     *     Video & Music, social apps → Social).
     *  3. Label keyword fallback for obfuscated packages.
     *  4. [AppCategory.OTHER].
     */
    fun categorize(packageName: String, label: String, systemCategory: Int): AppCategory {
        val pkg = packageName.lowercase()
        for ((prefix, category) in packageRules) {
            if (pkg.startsWith(prefix)) return category
        }
        when (systemCategory) {
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            -> return AppCategory.VIDEO_MUSIC
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_ACCESSIBILITY,
            -> return AppCategory.NEWS_PROD
        }
        val name = label.lowercase()
        for ((keyword, category) in labelRules) {
            if (name.contains(keyword)) return category
        }
        return AppCategory.OTHER
    }

    /** Longest prefix first so the most specific rule always wins. */
    private val packageRules: List<Pair<String, AppCategory>> = buildList {
        // Messaging (checked before generic "com.facebook").
        add("com.facebook.orca" to AppCategory.MESSAGING)
        add("com.whatsapp" to AppCategory.MESSAGING)
        add("org.telegram" to AppCategory.MESSAGING)
        add("com.tencent.mm" to AppCategory.MESSAGING)
        add("com.tencent.mobileqq" to AppCategory.MESSAGING)
        add("com.google.android.apps.messaging" to AppCategory.MESSAGING)
        add("com.google.android.talk" to AppCategory.MESSAGING)
        add("com.slack" to AppCategory.MESSAGING)
        add("com.microsoft.teams" to AppCategory.MESSAGING)
        add("com.skype" to AppCategory.MESSAGING)
        add("com.signal" to AppCategory.MESSAGING)
        add("org.thoughtcrime.securesms" to AppCategory.MESSAGING)
        add("com.viber" to AppCategory.MESSAGING)
        add("com.linecorp.line" to AppCategory.MESSAGING)

        // Social.
        add("com.facebook" to AppCategory.SOCIAL)
        add("com.instagram" to AppCategory.SOCIAL)
        add("com.zhiliaoapp.musically" to AppCategory.SOCIAL)
        add("com.reddit" to AppCategory.SOCIAL)
        add("com.snapchat" to AppCategory.SOCIAL)
        add("com.twitter" to AppCategory.SOCIAL)
        add("com.linkedin" to AppCategory.SOCIAL)
        add("com.pinterest" to AppCategory.SOCIAL)
        add("com.tumblr" to AppCategory.SOCIAL)
        add("com.quora" to AppCategory.SOCIAL)
        add("com.discord" to AppCategory.SOCIAL)
        add("com.twitch" to AppCategory.SOCIAL)
        add("com.tinder" to AppCategory.SOCIAL)
        add("com.badoo" to AppCategory.SOCIAL)
        add("com.hinge" to AppCategory.SOCIAL)
        add("com.google.android.apps.plus" to AppCategory.SOCIAL)

        // Video & Music.
        add("com.google.android.youtube" to AppCategory.VIDEO_MUSIC)
        add("com.spotify" to AppCategory.VIDEO_MUSIC)
        add("com.netflix" to AppCategory.VIDEO_MUSIC)
        add("com.amazon.avod" to AppCategory.VIDEO_MUSIC)
        add("com.disney" to AppCategory.VIDEO_MUSIC)
        add("com.hulu" to AppCategory.VIDEO_MUSIC)
        add("com.hbo" to AppCategory.VIDEO_MUSIC)
        add("com.crunchyroll" to AppCategory.VIDEO_MUSIC)
        add("com.soundcloud" to AppCategory.VIDEO_MUSIC)
        add("com.apple.android.music" to AppCategory.VIDEO_MUSIC)
        add("com.pandora" to AppCategory.VIDEO_MUSIC)
        add("com.deezer" to AppCategory.VIDEO_MUSIC)
        add("com.tidal" to AppCategory.VIDEO_MUSIC)
        add("com.google.android.apps.youtube.music" to AppCategory.VIDEO_MUSIC)
        add("com.google.android.videos" to AppCategory.VIDEO_MUSIC)
        add("com.google.android.apps.music" to AppCategory.VIDEO_MUSIC)
        add("com.plexapp" to AppCategory.VIDEO_MUSIC)
        add("com.vimeo" to AppCategory.VIDEO_MUSIC)
        add("com.dailymotion" to AppCategory.VIDEO_MUSIC)
        add("com.bilibili" to AppCategory.VIDEO_MUSIC)
        add("com.youku" to AppCategory.VIDEO_MUSIC)
        add("com.tencent.qqlive" to AppCategory.VIDEO_MUSIC)
        add("org.videolan.vlc" to AppCategory.VIDEO_MUSIC)

        // Shopping — marketplaces, e-commerce, online stores, and the order /
        // delivery apps that are part of the buying flow.
        add("com.amazon.mshop" to AppCategory.SHOPPING)
        add("com.walmart" to AppCategory.SHOPPING)
        add("com.alibaba.aliexpresshd" to AppCategory.SHOPPING)
        add("com.alibaba.intl.android.apps.poseidon" to AppCategory.SHOPPING)
        add("com.ebay.mobile" to AppCategory.SHOPPING)
        add("com.shein" to AppCategory.SHOPPING)
        add("com.taobao" to AppCategory.SHOPPING)
        add("com.tmall" to AppCategory.SHOPPING)
        add("com.flipkart" to AppCategory.SHOPPING)
        add("com.etsy" to AppCategory.SHOPPING)
        add("com.wish" to AppCategory.SHOPPING)
        add("com.einnovation.temu" to AppCategory.SHOPPING)
        add("com.mercado" to AppCategory.SHOPPING)
        add("com.shopee" to AppCategory.SHOPPING)
        add("com.lazada" to AppCategory.SHOPPING)
        add("com.myntra.android" to AppCategory.SHOPPING)
        add("com.nykaa" to AppCategory.SHOPPING)
        add("com.meesho.supply" to AppCategory.SHOPPING)
        add("com.target.ui" to AppCategory.SHOPPING)
        add("com.bestbuy.android" to AppCategory.SHOPPING)
        add("com.thehomedepot" to AppCategory.SHOPPING)
        add("com.costco" to AppCategory.SHOPPING)
        add("com.kroger" to AppCategory.SHOPPING)
        add("com.sephora" to AppCategory.SHOPPING)
        add("com.zalando.android" to AppCategory.SHOPPING)
        add("com.asos.app" to AppCategory.SHOPPING)
        add("com.hm.hmapp" to AppCategory.SHOPPING)
        add("com.wayfair" to AppCategory.SHOPPING)
        add("com.macys" to AppCategory.SHOPPING)
        add("com.nordstrom" to AppCategory.SHOPPING)
        add("com.groupon" to AppCategory.SHOPPING)
        add("com.rakuten" to AppCategory.SHOPPING)
        add("com.swiggy" to AppCategory.SHOPPING)
        add("com.zomato" to AppCategory.SHOPPING)
        add("com.foodpanda" to AppCategory.SHOPPING)
        add("com.doordash" to AppCategory.SHOPPING)
        add("com.ubercab.eats" to AppCategory.SHOPPING)
        add("com.deliveroo" to AppCategory.SHOPPING)
        add("com.justeat" to AppCategory.SHOPPING)
        add("com.grubhub" to AppCategory.SHOPPING)
        add("com.postmates" to AppCategory.SHOPPING)
        add("com.instacart" to AppCategory.SHOPPING)
        add("com.fedex" to AppCategory.SHOPPING)
        add("com.ups.mobile.android" to AppCategory.SHOPPING)
        add("com.dhl" to AppCategory.SHOPPING)
        add("com.usps" to AppCategory.SHOPPING)

        // Payment — banking, banking transactions, digital wallets and mobile
        // financial services.
        add("com.paypal.android.p2pmobile" to AppCategory.PAYMENT)
        add("com.venmo" to AppCategory.PAYMENT)
        add("com.squareup.cash" to AppCategory.PAYMENT)
        add("com.google.android.apps.walletnfcrel" to AppCategory.PAYMENT)
        add("com.google.android.apps.nbu.paisa.user" to AppCategory.PAYMENT)
        add("com.phonepe.app" to AppCategory.PAYMENT)
        add("net.one97.paytm" to AppCategory.PAYMENT)
        add("com.revolut" to AppCategory.PAYMENT)
        add("transferwise" to AppCategory.PAYMENT)
        add("com.transferwise.android" to AppCategory.PAYMENT)
        add("com.monzo" to AppCategory.PAYMENT)
        add("co.banking.android" to AppCategory.PAYMENT)
        add("mobile.chimebank" to AppCategory.PAYMENT)
        add("com.chase.sig.android" to AppCategory.PAYMENT)
        add("com.infonow.bofa" to AppCategory.PAYMENT)
        add("com.wf.wellsfargomobile" to AppCategory.PAYMENT)
        add("com.citi.citimobile" to AppCategory.PAYMENT)
        add("com.konyl.capitalone" to AppCategory.PAYMENT)
        add("com.usbank" to AppCategory.PAYMENT)
        add("com.tdbank" to AppCategory.PAYMENT)
        add("com.pnc.ecommerce.mobile" to AppCategory.PAYMENT)
        add("com.sofi.app" to AppCategory.PAYMENT)
        add("com.n26.android" to AppCategory.PAYMENT)
        add("com.klarna.android" to AppCategory.PAYMENT)
        add("com.affirm.android" to AppCategory.PAYMENT)
        add("com.zellepay.zelle" to AppCategory.PAYMENT)
        add("com.robinhood.android" to AppCategory.PAYMENT)
        add("com.coinbase.android" to AppCategory.PAYMENT)
        add("com.eg.android.AlipayGphone" to AppCategory.PAYMENT)
        add("com.samsung.android.spay" to AppCategory.PAYMENT)
        add("com.google.android.apps.googlepay" to AppCategory.PAYMENT)
        add("com.starlingbank.android" to AppCategory.PAYMENT)
        add("com.remitly" to AppCategory.PAYMENT)
        add("com.payoneer.android" to AppCategory.PAYMENT)
        add("com.dave" to AppCategory.PAYMENT)
        add("com.moneylion" to AppCategory.PAYMENT)
        add("com.empower.llc" to AppCategory.PAYMENT)
        add("com.creditkarma" to AppCategory.PAYMENT)
        add("com.rh.binance" to AppCategory.PAYMENT)
        add("com.crypto.exchange" to AppCategory.PAYMENT)

        // Games.
        add("com.supercell" to AppCategory.GAMES)
        add("com.king." to AppCategory.GAMES)
        add("com.rovio" to AppCategory.GAMES)
        add("com.roblox" to AppCategory.GAMES)
        add("com.mojang" to AppCategory.GAMES)
        add("com.nianticlabs" to AppCategory.GAMES)
        add("com.tencent.ig" to AppCategory.GAMES)
        add("com.moonton" to AppCategory.GAMES)
        add("com.kiloo" to AppCategory.GAMES)
        add("com.epicgames" to AppCategory.GAMES)
        add("com.ea.games" to AppCategory.GAMES)
        add("com.activision" to AppCategory.GAMES)
        add("com.igg." to AppCategory.GAMES)
        add("com.gameloft" to AppCategory.GAMES)
        add("com.zynga" to AppCategory.GAMES)
        add("com.playrix" to AppCategory.GAMES)
        add("com.zeptolab" to AppCategory.GAMES)
        add("com.outfit7" to AppCategory.GAMES)
        add("com.scopely" to AppCategory.GAMES)
        add("com.garena" to AppCategory.GAMES)
        add("com.mihoyo" to AppCategory.GAMES)
        add("com.hoyoverse" to AppCategory.GAMES)
        add("com.netease." to AppCategory.GAMES)
        add("com.dts.freefireth" to AppCategory.GAMES)

        // News & Productivity (prototype's catch-all: browsers, mail, docs,
        // maps, tools all live here).
        add("com.android.chrome" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.chrome" to AppCategory.NEWS_PROD)
        add("com.microsoft.emmx" to AppCategory.NEWS_PROD)
        add("org.mozilla.firefox" to AppCategory.NEWS_PROD)
        add("com.opera.browser" to AppCategory.NEWS_PROD)
        add("com.duckduckgo" to AppCategory.NEWS_PROD)
        add("com.google.android.gm" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.docs" to AppCategory.NEWS_PROD)
        add("com.google.android.calendar" to AppCategory.NEWS_PROD)
        add("com.microsoft.office" to AppCategory.NEWS_PROD)
        add("com.microsoft.todos" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.maps" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.photos" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.books" to AppCategory.NEWS_PROD)
        add("com.google.android.googlequicksearchbox" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.keeptasks" to AppCategory.NEWS_PROD)
        add("com.google.android.apps.nexuslauncher" to AppCategory.NEWS_PROD)
        add("com.microsoft.launcher" to AppCategory.NEWS_PROD)
        add("com.notion" to AppCategory.NEWS_PROD)
        add("com.todoist" to AppCategory.NEWS_PROD)
        add("com.evernote" to AppCategory.NEWS_PROD)
        add("com.trello" to AppCategory.NEWS_PROD)
        add("com.asana" to AppCategory.NEWS_PROD)
        add("com.dropbox" to AppCategory.NEWS_PROD)
    }.sortedByDescending { it.first.length }

    /** Longest keyword first so compound labels win over single words. */
    private val labelRules: List<Pair<String, AppCategory>> = buildList {
        add("clash of clans" to AppCategory.GAMES)
        add("subway surfers" to AppCategory.GAMES)
        add("candy crush" to AppCategory.GAMES)
        add("free fire" to AppCategory.GAMES)
        add("stumble guys" to AppCategory.GAMES)
        add("temple run" to AppCategory.GAMES)
        add("angry birds" to AppCategory.GAMES)
        add("pokémon go" to AppCategory.GAMES)
        add("pokemon go" to AppCategory.GAMES)
        add("among us" to AppCategory.GAMES)
        add("minecraft" to AppCategory.GAMES)
        add("roblox" to AppCategory.GAMES)
        add("pubg" to AppCategory.GAMES)
        add("apple music" to AppCategory.VIDEO_MUSIC)
        add("prime video" to AppCategory.VIDEO_MUSIC)
        add("disney plus" to AppCategory.VIDEO_MUSIC)
        add("youtube" to AppCategory.VIDEO_MUSIC)
        add("netflix" to AppCategory.VIDEO_MUSIC)
        add("spotify" to AppCategory.VIDEO_MUSIC)
        add("disney+" to AppCategory.VIDEO_MUSIC)
        add("crunchyroll" to AppCategory.VIDEO_MUSIC)
        add("soundcloud" to AppCategory.VIDEO_MUSIC)
        add("pandora" to AppCategory.VIDEO_MUSIC)
        add("deezer" to AppCategory.VIDEO_MUSIC)
        add("tidal" to AppCategory.VIDEO_MUSIC)
        add("bilibili" to AppCategory.VIDEO_MUSIC)
        add("vlc" to AppCategory.VIDEO_MUSIC)
        add("hulu" to AppCategory.VIDEO_MUSIC)
        add("whatsapp" to AppCategory.MESSAGING)
        add("telegram" to AppCategory.MESSAGING)
        add("wechat" to AppCategory.MESSAGING)
        add("messenger" to AppCategory.MESSAGING)
        add("slack" to AppCategory.MESSAGING)
        add("teams" to AppCategory.MESSAGING)
        add("skype" to AppCategory.MESSAGING)
        add("signal" to AppCategory.MESSAGING)
        add("viber" to AppCategory.MESSAGING)
        add("facebook" to AppCategory.SOCIAL)
        add("instagram" to AppCategory.SOCIAL)
        add("tiktok" to AppCategory.SOCIAL)
        add("reddit" to AppCategory.SOCIAL)
        add("snapchat" to AppCategory.SOCIAL)
        add("twitter" to AppCategory.SOCIAL)
        add("linkedin" to AppCategory.SOCIAL)
        add("pinterest" to AppCategory.SOCIAL)
        add("discord" to AppCategory.SOCIAL)
        add("twitch" to AppCategory.SOCIAL)
        add("tumblr" to AppCategory.SOCIAL)
        add("quora" to AppCategory.SOCIAL)
        add("tinder" to AppCategory.SOCIAL)
        add("amazon" to AppCategory.SHOPPING)
        add("aliexpress" to AppCategory.SHOPPING)
        add("flipkart" to AppCategory.SHOPPING)
        add("shein" to AppCategory.SHOPPING)
        add("ebay" to AppCategory.SHOPPING)
        add("etsy" to AppCategory.SHOPPING)
        add("temu" to AppCategory.SHOPPING)
        add("walmart" to AppCategory.SHOPPING)
        add("mercado" to AppCategory.SHOPPING)
        add("shopee" to AppCategory.SHOPPING)
        add("lazada" to AppCategory.SHOPPING)
        add("myntra" to AppCategory.SHOPPING)
        add("nykaa" to AppCategory.SHOPPING)
        add("meesho" to AppCategory.SHOPPING)
        add("target" to AppCategory.SHOPPING)
        add("best buy" to AppCategory.SHOPPING)
        add("home depot" to AppCategory.SHOPPING)
        add("costco" to AppCategory.SHOPPING)
        add("sephora" to AppCategory.SHOPPING)
        add("zalando" to AppCategory.SHOPPING)
        add("asos" to AppCategory.SHOPPING)
        add("wayfair" to AppCategory.SHOPPING)
        add("groupon" to AppCategory.SHOPPING)
        add("marketplace" to AppCategory.SHOPPING)
        add("market" to AppCategory.SHOPPING)
        add("shopping" to AppCategory.SHOPPING)
        add("swiggy" to AppCategory.SHOPPING)
        add("zomato" to AppCategory.SHOPPING)
        add("foodpanda" to AppCategory.SHOPPING)
        add("delivery" to AppCategory.SHOPPING)
        add("grocery" to AppCategory.SHOPPING)
        add("supermarket" to AppCategory.SHOPPING)
        add("coupon" to AppCategory.SHOPPING)
        add("cashback" to AppCategory.SHOPPING)
        add("doordash" to AppCategory.SHOPPING)
        add("uber eats" to AppCategory.SHOPPING)
        add("deliveroo" to AppCategory.SHOPPING)
        add("just eat" to AppCategory.SHOPPING)
        add("grubhub" to AppCategory.SHOPPING)
        add("instacart" to AppCategory.SHOPPING)
        add("fedex" to AppCategory.SHOPPING)
        add("usps" to AppCategory.SHOPPING)
        add("dhl" to AppCategory.SHOPPING)
        add("paypal" to AppCategory.PAYMENT)
        add("venmo" to AppCategory.PAYMENT)
        add("cash app" to AppCategory.PAYMENT)
        add("google pay" to AppCategory.PAYMENT)
        add("apple pay" to AppCategory.PAYMENT)
        add("samsung pay" to AppCategory.PAYMENT)
        add("phonepe" to AppCategory.PAYMENT)
        add("paytm" to AppCategory.PAYMENT)
        add("revolut" to AppCategory.PAYMENT)
        add("wise" to AppCategory.PAYMENT)
        add("monzo" to AppCategory.PAYMENT)
        add("starling" to AppCategory.PAYMENT)
        add("chime" to AppCategory.PAYMENT)
        add("sofi" to AppCategory.PAYMENT)
        add("n26" to AppCategory.PAYMENT)
        add("klarna" to AppCategory.PAYMENT)
        add("affirm" to AppCategory.PAYMENT)
        add("zelle" to AppCategory.PAYMENT)
        add("robinhood" to AppCategory.PAYMENT)
        add("coinbase" to AppCategory.PAYMENT)
        add("alipay" to AppCategory.PAYMENT)
        add("remitly" to AppCategory.PAYMENT)
        add("bitcoin" to AppCategory.PAYMENT)
        add("crypto" to AppCategory.PAYMENT)
        add("wallet" to AppCategory.PAYMENT)
        add("banking" to AppCategory.PAYMENT)
        add("bank" to AppCategory.PAYMENT)
        add("payment" to AppCategory.PAYMENT)
        add("upi" to AppCategory.PAYMENT)
        add("chrome" to AppCategory.NEWS_PROD)
        add("firefox" to AppCategory.NEWS_PROD)
        add("gmail" to AppCategory.NEWS_PROD)
        add("google news" to AppCategory.NEWS_PROD)
        add("outlook" to AppCategory.NEWS_PROD)
        add("powerpoint" to AppCategory.NEWS_PROD)
        add("excel" to AppCategory.NEWS_PROD)
        add("calculator" to AppCategory.NEWS_PROD)
        add("calendar" to AppCategory.NEWS_PROD)
        add("dropbox" to AppCategory.NEWS_PROD)
        add("notion" to AppCategory.NEWS_PROD)
        add("trello" to AppCategory.NEWS_PROD)
        add("evernote" to AppCategory.NEWS_PROD)
    }.sortedByDescending { it.first.length }
}
