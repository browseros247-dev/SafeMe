# SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)

This document describes the **bundled** keyword & website dataset that ships with SafeMe. Every entry count below is machine-generated from the sources and CI-enforced — see `tools/ci/check_bundled_counts.py` (do not hand-edit the `BUNDLED-GEN` blocks).

## IMPORTANT — Engine-Only Usage
- The **UI NEVER lists** these bundled entries. The Keyword Manager shows **only user-custom** entries; the Blocking screen shows a single aggregate keyword count (custom + bundled) derived live from code.
- The accessibility blocking engine, the browser URL gate, and the image/video-search gate merge this bundled dataset with the user's custom entries **at match time**. Custom lists are scanned first, so a hit present in both is attributed to the custom entry.
- **Keyword matching:** case-insensitive substring containment against window text / title / contentDescription / URL.
- **Website matching:** domain-suffix (host ends-with) match at label boundaries, e.g. `tiktok.com` also matches `www.tiktok.com` and `m.tiktok.com`.
- **Whitelist overrides** the blocklist (both bundled and custom).
- The master **Blocking** toggle in the Blocking screen gates the whole engine.
- The dataset is **compiled into the app** as Kotlin constants (`data/BundledAdult.kt`, `data/BundledKeywordCatalog.kt`). There is no runtime parse/load step and no fallback path.

## Dataset totals (ADULT ONLY)

<!-- BUNDLED-GEN:START summary -->
| Source | Entries |
|---|---|
| Curated adult keywords (`bundledAdultKeywords`) | 20 |
| Curated adult websites (`bundledAdultWebsites`) | 113 |
| NopoX catalog verbatim (`blockKeywordsByLanguage`, 37 languages) | 1189 |
| **Bundled blocklist grand total** | **1322** |
| Recovery whitelist seed (not blocklist) | 20 |

EN-device effective pools: keyword matching 454 raw / 448 unique (6 curated–catalog overlaps); URL-gate domains 211 raw / 204 unique (7 overlaps). Keywords use the device-language list with English fallback; URL-gate domains are always EN + device-language.
<!-- BUNDLED-GEN:END summary -->

> Note: everything bundled is adult-category content (`BlockedCategory.ADULT`). Broad category filters are intentionally absent. Users can still add their own custom entries in any category via the Keyword Manager.

## Sources

### 1. Curated adult list (`data/BundledAdult.kt`)

Hand-picked high-signal adult keywords and sites, always active regardless of device language.

<!-- BUNDLED-GEN:START adult-keywords -->
## ADULT — Keywords (20)
`xvideos`, `xhamster`, `xnxx`, `youporn`, `tube8`, `spankbang`, `brazzers`, `bangbros`, `naughtyamerica`, `realitykings`, `vixen`, `camgirl`, `webcamsex`, `sexchat`, `sexting`, `oral sex`, `sextoys`, `sex toy`, `sexdoll`, `pussy`
<!-- BUNDLED-GEN:END adult-keywords -->

<!-- BUNDLED-GEN:START adult-websites -->
## ADULT — Websites (113)
`pornhub.com`, `xvideos.com`, `xhamster.com`, `xnxx.com`, `youporn.com`, `redtube.com`, `tube8.com`, `spankbang.com`, `brazzers.com`, `bangbros.com`, `naughtyamerica.com`, `realitykings.com`, `digitalplayground.com`, `vixen.com`, `blacked.com`, `tushy.com`, `milf.com`, `evilangel.com`, `nubiles.net`, `teamskeet.com`, `mofos.com`, `czechav.com`, `czechstreets.com`, `legalporno.com`, `hardx.com`, `julesjordan.com`, `bang.com`, `realkingporn.com`, `pornpros.com`, `webyoung.com`, `sweetheartvideo.com`, `sweetxfilms.com`, `familystrokes.com`, `mompov.com`, `povd.com`, `povr.com`, `vrporn.com`, `babevr.com`, `wankzvr.com`, `vrbangers.com`, `badoinkvr.com`, `virtualrealporn.com`, `czechvr.com`, `naughtymilkshake.com`, `milked.com`, `kinkvr.com`, `hclips.com`, `hdsex.org`, `hdzog.com`, `hdporntube.com`, `hotmovs.com`, `javhub.net`, `javhd.com`, `javlibrary.com`, `javdatabase.com`, `javgg.com`, `hentai.tv`, `hanime.tv`, `hentaihaven.org`, `hdoujin.com`, `nhentai.net`, `e-hentai.org`, `exhentai.org`, `pururin.io`, `tsumino.com`, `hentai2read.com`, `readhentaimanga.com`, `hitomi.la`, `rule34.xxx`, `literotica.com`, `asstr.org`, `adultfriendfinder.com`, `passion.com`, `ashleymadison.com`, `streamate.com`, `myfreecams.com`, `chaturbate.com`, `stripchat.com`, `livejasmin.com`, `bongacams.com`, `cam4.com`, `camsoda.com`, `xcams.com`, `flirt4free.com`, `imlive.com`, `adultwork.com`, `camster.com`, `sexchat.com`, `outpersonals.com`, `victoriamilan.com`, `sexdolls.com`, `bad-dragon.com`, `baddragon.com`, `onlyfans.com`, `fansly.com`, `manyvids.com`, `porntrex.com`, `eporner.com`, `hqporner.com`, `sxyprn.com`, `xnxx.tv`, `xvideos.red`, `pornhub.org`, `pornhub.net`, `phncdn.com`, `trafficjunky.net`, `tnaflix.com`, `drtuber.com`, `sunporno.com`, `nuvid.com`, `beeg.com`, `youjizz.com`, `motherless.com`
<!-- BUNDLED-GEN:END adult-websites -->

### 2. NopoX catalog (`data/BundledKeywordCatalog.kt`)

Multi-language blocking catalog ported verbatim from the NopoX reference payload; entries intentionally mix plain terms and site domains. Selection contract: keyword matching uses ONLY the device-language list with English fallback (`blockKeywordsFor`); URL-gate domain coverage is always the EN domains plus the device-language domains (`domainsFor`). An entry counts as a domain when it contains `.` and no space (`isDomainEntry`).

<details>

<!-- BUNDLED-GEN:START catalog-table -->
<summary>Per-language table (37 languages, source order — generated)</summary>

| Language | Total | Plain keywords | Domains |
|---|---|---|---|
| `nn` | 3 | 3 | 0 |
| `de` | 8 | 8 | 0 |
| `hi` | 19 | 19 | 0 |
| `no` | 3 | 3 | 0 |
| `fi` | 13 | 13 | 0 |
| `ru` | 23 | 23 | 0 |
| `pt` | 133 | 133 | 0 |
| `fil` | 2 | 2 | 0 |
| `lt` | 13 | 13 | 0 |
| `bn` | 20 | 20 | 0 |
| `fr` | 8 | 8 | 0 |
| `hu` | 15 | 15 | 0 |
| `sk` | 19 | 19 | 0 |
| `sl` | 11 | 11 | 0 |
| `ur` | 17 | 17 | 0 |
| `sv` | 15 | 15 | 0 |
| `ko` | 4 | 4 | 0 |
| `in` | 11 | 11 | 0 |
| `ms` | 9 | 9 | 0 |
| `el` | 8 | 8 | 0 |
| `en` | 532 | 434 | 98 |
| `ku` | 5 | 5 | 0 |
| `it` | 8 | 8 | 0 |
| `es` | 132 | 132 | 0 |
| `iw` | 19 | 19 | 0 |
| `zh` | 8 | 8 | 0 |
| `cs` | 15 | 15 | 0 |
| `ar` | 18 | 18 | 0 |
| `vi` | 9 | 9 | 0 |
| `nb` | 3 | 3 | 0 |
| `th` | 7 | 7 | 0 |
| `ja` | 11 | 11 | 0 |
| `fa` | 19 | 19 | 0 |
| `pl` | 12 | 12 | 0 |
| `da` | 3 | 3 | 0 |
| `nl` | 19 | 19 | 0 |
| `tr` | 15 | 15 | 0 |
<!-- BUNDLED-GEN:END catalog-table -->

</details>

### 3. Recovery whitelist seed

One-time English whitelist seed for recovery-oriented terms (counted in the totals above, not blocklist). Entries are intentionally not inlined here; B9 tracks the seed's suppression semantics.

## Integrity

<!-- BUNDLED-GEN:START integrity -->
- **1322** bundled blocklist entries: 133 curated adult (20 keywords + 113 websites) + 1189 NopoX catalog verbatim (37 languages), plus a 20-entry recovery whitelist seed.
- EN reference split: 532 entries = 434 plain keywords + 98 domains.
- Counts are machine-generated from sources (`tools/ci/check_bundled_counts.py`, CI-enforced); do not hand-edit the generated blocks.
- Compiled into the app as Kotlin constants; read-only at runtime, never modified by user actions.
<!-- BUNDLED-GEN:END integrity -->

- Only **Adult** content is pre-installed for filtering.
- The **UI lists only user-custom** keywords & websites (plus one aggregate count).
- Engine merges bundled + custom at match time; custom-first attribution on conflict.
