# Task report — M1 fixture harvest

Date: 2026-07-28
Branch: m1/phase0-fixtures-and-ports (files created only; nothing committed)
Location: `tool/src/test/resources/fixtures/`
Source: live English Wikipedia API (`https://en.wikipedia.org/w/api.php`), `format=json&formatversion=2`, GET only, requests spaced ~1 s apart, User-Agent `LightWiki-fixture-harvest/0.1 (+https://github.com/tyleryancey/light-wiki; tyleryancey5@gmail.com)`.

## Fixtures

| File | Article title | Source URL | Retrieved | Bytes | Scan |
|---|---|---|---|---|---|
| `api/search-mercury.json` | (search: "mercury", srlimit=20) | https://en.wikipedia.org/wiki/Special:Search?search=mercury | 2026-07-28 | 7,038 | PASS |
| `api/pageprops-mercury.json` | Mercury | https://en.wikipedia.org/wiki/Mercury | 2026-07-28 | 118 | PASS |
| `api/pageprops-mercury-element.json` | Mercury (element) | https://en.wikipedia.org/wiki/Mercury_(element) | 2026-07-28 | 97 | PASS |
| `api/parse-stub.json` | Vestmanna (full parse envelope) | https://en.wikipedia.org/wiki/Vestmanna | 2026-07-28 | 45,269 | PASS |
| `articles/mercury-element.html` | Mercury (element) | https://en.wikipedia.org/wiki/Mercury_(element) | 2026-07-28 | 629,515 | PASS |
| `articles/mercury-disambiguation.html` | Mercury | https://en.wikipedia.org/wiki/Mercury | 2026-07-28 | 32,563 | PASS |
| `articles/marie-curie.html` | Marie Curie | https://en.wikipedia.org/wiki/Marie_Curie | 2026-07-28 | 487,433 | PASS |
| `articles/fourier-transform.html` | Fourier transform | https://en.wikipedia.org/wiki/Fourier_transform | 2026-07-28 | 1,643,251 | PASS |
| `articles/list-countries-population-un.html` | List of countries and dependencies by population (United Nations) | https://en.wikipedia.org/wiki/List_of_countries_and_dependencies_by_population_(United_Nations) | 2026-07-28 | 307,451 | PASS |
| `articles/list-presidents-us.html` | List of presidents of the United States | https://en.wikipedia.org/wiki/List_of_presidents_of_the_United_States | 2026-07-28 | 337,994 | PASS |
| `articles/vestmanna.html` | Vestmanna (short stub) | https://en.wikipedia.org/wiki/Vestmanna | 2026-07-28 | 43,716 | PASS |
| `articles/gettysburg-address.html` | Gettysburg Address (blockquote-heavy) | https://en.wikipedia.org/wiki/Gettysburg_Address | 2026-07-28 | 389,133 | PASS |
| `articles/great-wave-kanagawa.html` | The Great Wave off Kanagawa (image/caption-rich) | https://en.wikipedia.org/wiki/The_Great_Wave_off_Kanagawa | 2026-07-28 | 238,656 | PASS |
| `articles/outline-of-chemistry.html` | Outline of chemistry (nested-list-heavy) | https://en.wikipedia.org/wiki/Outline_of_chemistry | 2026-07-28 | 129,719 | PASS |
| `articles/mary-anning.html` | Mary Anning (plain mid-length bio) | https://en.wikipedia.org/wiki/Mary_Anning | 2026-07-28 | 293,726 | PASS |
| `articles/caffeine.html` | Caffeine (chembox/drugbox infobox variant) | https://en.wikipedia.org/wiki/Caffeine | 2026-07-28 | 1,072,899 | PASS |
| `README.md` | (attribution/index) | — | 2026-07-28 | 5,814 | PASS |

Total: 17 files, ~5.4 MiB (5,664,392 bytes). No swaps were needed — every candidate passed the scan on the first attempt.

## Scan-vet

Authoritative lists: `plugin/src/main/kotlin/com/thelightphone/plugin/LightSdkPlugin.kt` — `BLOCKED_IMPORTS` (16 fixed strings) and `BLOCKED_CODE_PATTERNS` (29 regexes). The 16 import prefixes were vetted as fixed strings (`grep -F`); the 29 code-pattern regexes were vetted verbatim as PCRE (`grep -P`, supported by the installed grep, ugrep 7.5.0 — the Kotlin `Regex` syntax used (`\b`, `\s`, `\??`, non-capturing groups) is PCRE-compatible, so no per-pattern translation was needed).

Pattern files (one pattern per line, exact contents):

`blocked-imports.txt` — the 16 `BLOCKED_IMPORTS` strings verbatim (android.app., android.content.Context, android.content.Intent, android.content.ComponentName, android.content.BroadcastReceiver, android.content.ContentProvider, android.content.ServiceConnection, androidx.compose.ui.platform.LocalContext, androidx.compose.ui.platform.LocalView, androidx.compose.ui.platform.LocalLifecycleOwner, androidx.lifecycle.compose.LocalLifecycleOwner, androidx.activity., androidx.appcompat., java.lang.reflect., java.lang.invoke., kotlin.reflect.).

`blocked-patterns.txt` — the 29 `BLOCKED_CODE_PATTERNS` regexes verbatim (`\bLocalContext\b`, `\bLocalView\b`, `\bLocalActivity\b`, `\bLocalLifecycleOwner\b`, `\bas\??\s+(?:\w+\.)*\w*Activity\b`, `\bas\??\s+(?:\w+\.)*(?:Context|ContextWrapper|ContextThemeWrapper|Application|Service|ContentProvider|BroadcastReceiver)\b`, `\bstartActivity\s*\(`, `\bstartService\s*\(`, `\bbindService\s*\(`, `\bregisterReceiver\s*\(`, `\bgetSystemService\s*\(`, `\bcontentResolver\b`, `\bgetBaseContext\s*\(`, `\battachBaseContext\s*\(`, `\bcreatePackageContext\s*\(`, `\bcreateConfigurationContext\s*\(`, `\bcreateDeviceProtectedStorageContext\s*\(`, `\bcreateContextForSplit\s*\(`, `\bcreateAttributionContext\s*\(`, `\bcreateWindowContext\s*\(`, `\bcreateDisplayContext\s*\(`, `\b\.javaClass\b`, `\b\.java\s*\.\s*\w`, `\bClass\s*\.\s*forName\s*\(`, `\b\.getDeclaredMethod\s*\(`, `\b\.getMethod\s*\(`, `\b\.getDeclaredField\s*\(`, `\b\.getField\s*\(`, `\bMethodHandles\b`).

Exact commands (run over the entire `fixtures/` tree including `README.md`):

```
grep -rFl -f blocked-imports.txt tool/src/test/resources/fixtures/   # exit 1 (no matches)
grep -rPl -f blocked-patterns.txt tool/src/test/resources/fixtures/  # exit 1 (no matches)
```

Both scans exited 1 (zero matches in zero files). Verdict: ALL PASS.

Caveat: grep matches per line, whereas the plugin's Kotlin `Regex.containsMatchIn` scans whole-file text, so a `\s` inside a pattern could in principle span a newline in the Kotlin scan but not in grep. The only patterns with interior `\s` are the `as ... Activity/Context/...` casts; none of the fixture files contain the `as`/`as?` keyword adjacent to any of those capitalized type names, so the line-based vet is not masking a cross-line match.

## Anomalies

- Redirects followed (both recorded in the parse response `.parse.redirects` and noted in `fixtures/README.md`):
  - `Mercury (disambiguation)` -> `Mercury` (the disambiguation page lives at the base title; `articles/mercury-disambiguation.html` is that page's body and pageprops confirms `disambiguation` flag on `Mercury`).
  - `List of countries by population (United Nations)` -> `List of countries and dependencies by population (United Nations)`.
- No `warnings` object appeared in any API response.
- Disambiguation polarity captured as required: `api/pageprops-mercury.json` has `"pageprops":{"disambiguation":""}`; `api/pageprops-mercury-element.json` has no `pageprops` key on the page object.
- Stub choice: Vestmanna (Faroese town) — genuine short article; full parse envelope is 45 KB, extracted body 43 KB, the smallest non-disambiguation fixture. `api/parse-stub.json` is the verbatim envelope from the same single request that produced `articles/vestmanna.html` (one fetch, two deliverables).
- Sizes: every article is well under the 2.5 MB cap; the largest are Fourier transform (1.64 MB, MathML-heavy) and Caffeine (1.07 MB). Mercury (element), the perf/gap-bug fixture, is 630 KB.
- All 12 article bodies start with the expected `<div class="mw-content-ltr mw-parser-output" ...>` wrapper; spot-checks confirmed infoboxes (Mercury (element), Marie Curie, Caffeine), `wikitable`s in both list articles, 11 `blockquote` hits in Gettysburg Address, and 736 `<math` elements in Fourier transform.
