package com.safeme.app.data

import java.util.Locale

object BundledKeywords {
    val keywords: List<BlockedKeyword> = buildList {
        bundledAdultKeywords.forEach { add(BlockedKeyword(it, BlockedCategory.ADULT)) }
        BundledKeywordCatalog
            .keywordsFor(Locale.getDefault().language)
            .forEach { add(BlockedKeyword(it.lowercase(), BlockedCategory.ADULT)) }
    }

    val websites: List<BlockedWebsite> = buildList {
        bundledAdultWebsites.forEach { add(BlockedWebsite(it, BlockedCategory.ADULT)) }
    }

    val totalCount: Int get() = keywords.size + websites.size
}
