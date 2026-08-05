# SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)

This document is the authoritative source for the **bundled** keyword & website dataset that ships with SafeMe.

## IMPORTANT — Engine-Only Usage
- The **UI NEVER renders** these bundled entries. The Keyword Manager UI displays **only user-custom** entries.
- The accessibility blocking engine merges this bundled dataset with the user's custom entries **at match time** (dedup by normalized value, user-custom wins on conflict).
- **Keyword matching:** case-insensitive substring containment against window text / title / contentDescription / URL.
- **Website matching:** domain-suffix (host ends-with) match at label boundaries, e.g. `tiktok.com` also matches `www.tiktok.com` and `m.tiktok.com`.
- **Whitelist overrides** the blocklist (both bundled and custom).
- The master **Blocking** toggle in the Blocking screen gates the whole engine.

## Dataset totals (ADULT ONLY)
| Category | Keywords | Websites | Total |
|---|---|---|---|
| Adult | 21 | 93 | 114 |
| **Total** | **21** | **93** | **114** |

> Note: Only the explicitly curated adult keyword & website list below is pre-installed as the bundled blacklist. Everything else (including broad category filters) is intentionally absent. Users can still add their own custom entries in any category via the Keyword Manager.

## Integrity
- **114** total bundled entries: 21 keywords + 93 websites.
- Read-only, bundled with the app; never modified by user actions.
- If the bundled resource fails to parse, the engine degrades gracefully to user-custom entries only (never crashes).

---

## ADULT — Keywords (21)
`xvideos`, `xhamster`, `xnxx`, `youporn`, `tube8`, `spankbang`, `brazzers`, `bangbros`, `naughtyamerica`, `realitykings`, `vixen`, `camgirl`, `webcamsex`, `sexchat`, `sexting`, `xxx`, `oral sex`, `sextoys`, `sex toy`, `sexdoll`, `pussy`

## ADULT — Websites (93)
`pornhub.com`, `xvideos.com`, `xhamster.com`, `xnxx.com`, `youporn.com`, `redtube.com`, `tube8.com`, `spankbang.com`, `brazzers.com`, `bangbros.com`, `naughtyamerica.com`, `realitykings.com`, `digitalplayground.com`, `vixen.com`, `blacked.com`, `tushy.com`, `milf.com`, `evilangel.com`, `nubiles.net`, `teamskeet.com`, `mofos.com`, `czechav.com`, `czechstreets.com`, `legalporno.com`, `hardx.com`, `julesjordan.com`, `bang.com`, `realkingporn.com`, `pornpros.com`, `webyoung.com`, `sweetheartvideo.com`, `sweetxfilms.com`, `familystrokes.com`, `mompov.com`, `povd.com`, `povr.com`, `vrporn.com`, `babevr.com`, `wankzvr.com`, `vrbangers.com`, `badoinkvr.com`, `virtualrealporn.com`, `czechvr.com`, `naughtymilkshake.com`, `milked.com`, `kinkvr.com`, `hclips.com`, `hdsex.org`, `hdzog.com`, `hdporntube.com`, `hotmovs.com`, `javhub.net`, `javhd.com`, `javlibrary.com`, `javdatabase.com`, `javgg.com`, `hentai.tv`, `hanime.tv`, `hentaihaven.org`, `hdoujin.com`, `nhentai.net`, `e-hentai.org`, `exhentai.org`, `pururin.io`, `tsumino.com`, `hentai2read.com`, `readhentaimanga.com`, `hitomi.la`, `rule34.xxx`, `literotica.com`, `asstr.org`, `adultfriendfinder.com`, `passion.com`, `ashleymadison.com`, `streamate.com`, `myfreecams.com`, `chaturbate.com`, `stripchat.com`, `livejasmin.com`, `bongacams.com`, `cam4.com`, `camsoda.com`, `xcams.com`, `flirt4free.com`, `imlive.com`, `adultwork.com`, `camster.com`, `sexchat.com`, `outpersonals.com`, `victoriamilan.com`, `sexdolls.com`, `bad-dragon.com`, `baddragon.com`

## Integrity footer
- **114 total** bundled entries: 21 keywords + 93 websites.
- Only **Adult** content is pre-installed for filtering.
- The **UI displays only user-custom** keywords & websites.
- Engine merges bundled + custom at match time; user-custom wins on conflict (dedup by normalized lowercase value).
- If the bundled dataset fails to parse/load, the engine still never crashes — it degrades to custom-only.
