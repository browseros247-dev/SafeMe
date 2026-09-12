#!/usr/bin/env python3
"""Keep bundled-dataset.md counts in sync with the dataset sources (B11).

Parses the three compiled-in sources and regenerates the
``<!-- BUNDLED-GEN:START name --> ... <!-- BUNDLED-GEN:END name -->`` blocks
in bundled-dataset.md (names: summary, adult-keywords, adult-websites,
catalog-table, integrity). Section headings that carry counts live INSIDE
the generated blocks, so no hand-written number can drift.

Parsing rules (mirrors the Kotlin sources):
- ``"..."`` literals inside the ``bundledAdultKeywords`` /
  ``bundledAdultWebsites`` / ``recoveryWhitelistSeed`` ``listOf(...)`` blocks.
- ``"code" to listOf(...)`` for catalog languages; the code pattern
  ``[A-Za-z]{2,3}(?:-[A-Za-z]+)?`` deliberately allows 3-letter codes --
  ``"fil"`` is invisible to a naive 2-letter regex (36 langs / 1187 entries
  instead of the true 37 / 1189).
- Domain-vs-keyword split mirrors ``isDomainEntry`` exactly: an entry is a
  domain when it contains "." and no space.
- Overlaps are compared case-insensitively (matching lowercases needles).

The script derives every number from source and hardcodes zero counts. The
independent tripwire is ``BundledKeywordCatalogTest.verbatimPayloadCounts``,
which pins the catalog pins (37 languages, en 532, ...): a dataset edit
fails that test AND this check until both are refreshed.

Usage:
    python3 tools/ci/check_bundled_counts.py          # regenerate in place
    python3 tools/ci/check_bundled_counts.py --check  # exit 1 + diff on drift
"""

import difflib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "bundled-dataset.md"
ADULT_SRC = ROOT / "app/src/main/java/com/safeme/app/data/BundledAdult.kt"
CATALOG_SRC = ROOT / "app/src/main/java/com/safeme/app/data/BundledKeywordCatalog.kt"

BLOCKS = ("summary", "adult-keywords", "adult-websites", "catalog-table", "integrity")


def literals_in_listof(source: str, var_name: str) -> list[str]:
    m = re.search(rf"val {var_name}[^\n]*?listOf\((.*?)\)", source, re.S)
    if not m:
        raise SystemExit(f"error: {var_name} listOf(...) block not found")
    return re.findall(r'"([^"]+)"', m.group(1))


def is_domain(entry: str) -> bool:
    return "." in entry and " " not in entry


def measure() -> dict:
    adult_text = ADULT_SRC.read_text()
    catalog_text = CATALOG_SRC.read_text()
    adult_kw = literals_in_listof(adult_text, "bundledAdultKeywords")
    adult_ws = literals_in_listof(adult_text, "bundledAdultWebsites")
    seed = literals_in_listof(catalog_text, "recoveryWhitelistSeed")
    langs: list[tuple[str, list[str]]] = [
        (lang, re.findall(r'"([^"]+)"', body))
        for lang, body in re.findall(
            r'"([A-Za-z]{2,3}(?:-[A-Za-z]+)?)"\s+to\s+listOf\((.*?)\)', catalog_text, re.S
        )
    ]
    if not langs:
        raise SystemExit("error: no catalog language lists parsed")
    en_entries = dict(langs)["en"]
    en_kw = [e for e in en_entries if not is_domain(e)]
    en_dom = [e for e in en_entries if is_domain(e)]
    overlap_kw = {e.lower() for e in adult_kw} & {e.lower() for e in en_kw}
    overlap_dom = {e.lower() for e in adult_ws} & {e.lower() for e in en_dom}
    catalog_total = sum(len(entries) for _, entries in langs)
    return {
        "adult_kw": adult_kw,
        "adult_ws": adult_ws,
        "seed": seed,
        "langs": langs,
        "catalog_total": catalog_total,
        "en_kw": en_kw,
        "en_dom": en_dom,
        "kw_raw": len(adult_kw) + len(en_kw),
        "kw_unique": len({e.lower() for e in adult_kw} | {e.lower() for e in en_kw}),
        "dom_raw": len(adult_ws) + len(en_dom),
        "dom_unique": len({e.lower() for e in adult_ws} | {e.lower() for e in en_dom}),
        "overlap_kw": len(overlap_kw),
        "overlap_dom": len(overlap_dom),
    }


def render(name: str, d: dict) -> str:
    adult_kw, adult_ws = d["adult_kw"], d["adult_ws"]
    langs, seed = d["langs"], d["seed"]
    adult_sub = len(adult_kw) + len(adult_ws)
    grand = adult_sub + d["catalog_total"]
    if name == "summary":
        return (
            "| Source | Entries |\n"
            "|---|---|\n"
            f"| Curated adult keywords (`bundledAdultKeywords`) | {len(adult_kw)} |\n"
            f"| Curated adult websites (`bundledAdultWebsites`) | {len(adult_ws)} |\n"
            f"| NopoX catalog verbatim (`blockKeywordsByLanguage`, {len(langs)} languages) "
            f"| {d['catalog_total']} |\n"
            f"| **Bundled blocklist grand total** | **{grand}** |\n"
            f"| Recovery whitelist seed (not blocklist) | {len(seed)} |\n"
            "\n"
            f"EN-device effective pools: keyword matching {d['kw_raw']} raw / "
            f"{d['kw_unique']} unique ({d['overlap_kw']} curated–catalog overlaps); "
            f"URL-gate domains {d['dom_raw']} raw / {d['dom_unique']} unique "
            f"({d['overlap_dom']} overlaps). Keywords use the device-language list "
            "with English fallback; URL-gate domains are always EN + device-language."
        )
    if name == "adult-keywords":
        return f"## ADULT — Keywords ({len(adult_kw)})\n" + ", ".join(
            f"`{e}`" for e in adult_kw
        )
    if name == "adult-websites":
        return f"## ADULT — Websites ({len(adult_ws)})\n" + ", ".join(
            f"`{e}`" for e in adult_ws
        )
    if name == "catalog-table":
        rows = [
            f"<summary>Per-language table "
            f"({len(langs)} languages, source order — generated)</summary>",
            "",
            "| Language | Total | Plain keywords | Domains |",
            "|---|---|---|---|",
        ]
        for lang, entries in langs:
            dom = sum(1 for e in entries if is_domain(e))
            rows.append(f"| `{lang}` | {len(entries)} | {len(entries) - dom} | {dom} |")
        return "\n".join(rows)
    if name == "integrity":
        return (
            f"- **{grand}** bundled blocklist entries: {adult_sub} curated adult "
            f"({len(adult_kw)} keywords + {len(adult_ws)} websites) + "
            f"{d['catalog_total']} NopoX catalog verbatim ({len(langs)} languages), "
            f"plus a {len(seed)}-entry recovery whitelist seed.\n"
            f"- EN reference split: {len(d['en_kw']) + len(d['en_dom'])} entries = "
            f"{len(d['en_kw'])} plain keywords + {len(d['en_dom'])} domains.\n"
            "- Counts are machine-generated from sources "
            "(`tools/ci/check_bundled_counts.py`, CI-enforced); "
            "do not hand-edit the generated blocks.\n"
            "- Compiled into the app as Kotlin constants; read-only at runtime, "
            "never modified by user actions."
        )
    raise AssertionError(f"unknown block {name}")


def regenerate(doc_text: str, d: dict) -> str:
    for name in BLOCKS:
        start = f"<!-- BUNDLED-GEN:START {name} -->"
        end = f"<!-- BUNDLED-GEN:END {name} -->"
        if doc_text.count(start) != 1 or doc_text.count(end) != 1:
            raise SystemExit(f"error: markers for block '{name}' must appear exactly once")
        pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.S)
        doc_text = pattern.sub(f"{start}\n{render(name, d)}\n{end}", doc_text, count=1)
    return doc_text if doc_text.endswith("\n") else doc_text + "\n"


def main() -> int:
    check = len(sys.argv) > 1 and sys.argv[1] == "--check"
    if len(sys.argv) > 1 and sys.argv[1] not in ("--check", "--help", "-h"):
        print(__doc__)
        return 2
    if len(sys.argv) > 1 and sys.argv[1] in ("--help", "-h"):
        print(__doc__)
        return 0
    for path in (DOC, ADULT_SRC, CATALOG_SRC):
        if not path.is_file():
            raise SystemExit(f"error: required file missing: {path}")
    d = measure()
    current = DOC.read_text()
    updated = regenerate(current, d)
    if check:
        if current == updated:
            print(
                f"bundled-dataset.md counts OK "
                f"(adult {len(d['adult_kw'])}+{len(d['adult_ws'])}, "
                f"catalog {len(d['langs'])} langs/{d['catalog_total']}, "
                f"seed {len(d['seed'])})"
            )
            return 0
        diff = difflib.unified_diff(
            current.splitlines(), updated.splitlines(),
            fromfile="bundled-dataset.md", tofile="bundled-dataset.md (regenerated)",
            lineterm="",
        )
        print("\n".join(diff))
        print("\nerror: bundled-dataset.md is stale; run "
              "python3 tools/ci/check_bundled_counts.py and commit the result")
        return 1
    DOC.write_text(updated)
    print(f"regenerated BUNDLED-GEN blocks ({', '.join(BLOCKS)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
