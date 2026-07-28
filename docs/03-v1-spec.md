# 03 — Light Wiki v1: frozen scope and spec

Basis: Tyler's Phase-2 decisions (2026-07-28): **continue** via fresh scaffold + port; **hand-rolled parser, jsoup issue drafted in parallel**; **v1 = text + images + simple tables**. Facts inherit citations from 01/02; new SDK claims cite source directly (clone `light-sdk` @ `d2323e3`).

**What v1 is, in one sentence:** a search-only, link-stripped, native-Compose Wikipedia reader — type a search, pick a result, read one finite article, put the phone away.

Proposed identity (Tyler confirms before scaffold — the id is permanent): repo **`light-wiki`**, id **`dev.tyler.wiki`**, label **`Wiki`** (single-word labels match all five shipped siblings).

---

## 1. Scope freeze

### In v1 (the smallest set that is still the whole product)

| Area | Frozen scope |
|---|---|
| Search | Typed query via the SDK input pattern; max 20 results (`srlimit=20`), title + plain-text snippet with `…` truncation. No search history, no suggestions. |
| Disambiguation | `pageprops` check; native chooser list (bounded; namespace/red-link/interwiki filtering per `rendering-exclusions.md` §8). The only navigation surface in the app. |
| Article | Native Compose rendering of: headings (h2–h4), paragraphs with bold/italic inline spans, bulleted/ordered lists (nesting flattened to 2 levels), blockquotes, **inline images with captions (grayscale-filtered)**, **infobox as a key-value card placed after the lead paragraph** (D7), **other tables simplified** (text-only grid, horizontal scroll when wide), math as its fallback image where present. |
| Pipeline | Full port of the May semantics: appendix-section drop (D4), **all `<a>` unwrapped** (D2), clutter strip incl. hatnotes/dablinks/redirects/series-sidebars (D8), `sup.reference` removal, noscript-image promotion, `//`→`https:` src fix, infobox reflow. `rendering-exclusions.md` carries forward as the living spec (v4). |
| Reading controls | A/A text size (steps of 10, bounds 80–180, default 110 — D6 continuity), persisted via DataStore. The app's only setting. |
| Navigation | Search → (chooser) → article; within-session article stack so chooser→entry→back returns to the chooser (May behavior, `MainActivity.kt:48–52`), rebuilt on the SDK's own back-stack primitives. |
| Errors/offline | Calm mapped copy + Retry (port `ErrorMessages.kt`); airplane-mode-clean on every screen. |
| Chrome | `sdk:ui` throughout; `LightThemeTokens` only (no color literals); portrait-locked. |
| Source | `en.wikipedia.org` API + `upload.wikimedia.org` images. Nothing else, ever. |

### Resolutions of the May audit's open dispositions (`rendering-exclusions.md` §10) for v1

1. TemplateStyles `<style>` blocks / inline color styles / `bgcolor`: **structurally moot** — the native document model carries no styles and the renderer draws only theme tokens; the entire "stray color past the dark theme" bug class from the WebView era ceases to exist.
2. Color-encoded data cells: v1 renders cell **text** only; the color-to-glyph substitution idea → backlog.
3. "Not to be confused" hatnote carve-out: v1 keeps drop-all (shipped behavior); carve-out → backlog.
4. Galleries (`.gallery`, `.mw-gallery-*`): **dropped** in v1 (explicitly, in the strip list) → backlog.
5. Math: fallback-image path only (`.mwe-math-fallback-image`); no MathML → backlog.
6. IPA/pronunciation: keep as plain text (May recommendation stands).
7. Wide tables: simplified render + horizontal scroll (May "defer" resolved as: no grid-fidelity work in v1).
8. Orphan-caption bug (§10 last bullet): **superseded natively** — the model keeps image dimensions for aspect-ratio placeholder sizing, and a failed image load drops the whole figure (image + caption); no orphan captions by construction.

### Out — post-approval backlog (cut, not canceled)

Gallery rendering · color-cell glyph substitution · "Not to be confused" carve-out · MathML/table-grid fidelity · in-article section jump (finite TOC) · image tap-to-zoom · pinned/offline articles (would need a storage + privacy story argued in an updated defense) · additional Wikipedia languages · search-from-article shortcut. **Every backlog item ships only after the re-vetting question (§6.1) is answered.**

### Never (carried from the locked DON'T list — D2/D10; scope-change requires a plan amendment, not a milestone)

In-article links · history · bookmarks · related/recommended anything · random-article · notifications · background refresh · analytics/telemetry · third-party calls · accounts · **tip-jar** (May plan's "(Optional tip-jar)" is cut dead: commercial-adjacent flag for zero user value — 02 §1).

## 2. Screens and flows (SDK framework mapping)

Three screens, per-screen `LightViewModel<R>`, exactly one `@InitialScreen`, one `@EntryPoint object : LightEntryPoint` with empty hooks (no push, no jobs). (`sdk/client/src/main/kotlin/com/thelightphone/sdk/{LightScreen.kt:11,51, LightViewModel.kt:6, InitialScreen.kt:8, EntryPoint.kt:10–12}`.)

1. **SearchScreen** (`@InitialScreen`) — `LightTopBar` "Wiki"; `LightTextField` displays the current query (it is read-only by design — sdk-facts §11) and opens **`LightTextInputEditor`** (full-screen, `rememberTextFieldState` + `keyboardOptionsFlow`; the `examples/weather` typed-search pattern; `sdk/ui/.../LightTextInputEditor.kt:46,95`). Submit → results list (≤20 rows: title + snippet, `lightClickable`). States: idle (hint copy), loading, results, empty ("No articles found."), error+Retry.
2. **DisambiguationScreen** — chooser list of entries (title + description); tap → ArticleScreen for that title. Bounded; no other affordances.
3. **ArticleScreen** — `LightTopBar` with ellipsized title; body = `LazyColumn` of rendered blocks; bottom bar: small-A / large-A `LightBarButton`s. Back pops the article stack (chooser round-trips preserved), then returns to Search.

Flow: Search → submit → if `isDisambiguation(title)` → chooser → article; else article directly (May flow, `WikipediaRepository.kt` + `MainActivity.kt`). Tapping a chooser entry pushes; back pops. Process death: no restoration — reopening lands on Search (deliberate: no history, D10; SDK back stack is in-memory anyway, sdk-facts §12).

## 3. Data model and pipeline

```
tool/src/main/kotlin/dev/tyler/wiki/
  ToolEntryPoint.kt                 @EntryPoint, empty hooks
  data/      WikiApi.kt             Ktor client (OkHttp engine — examples/weather/WeatherApi.kt:3–7 pattern),
                                    3 endpoints (search / pageprops / parse), kotlinx-serialization DTOs,
                                    UA "LightWiki/<version> (+https://github.com/tyleryancey/light-wiki)"
             WikiRepository.kt      bounded LRUs (search 32 / disambig 64 / article-model 16), suspend facade
  parser/    HtmlLexer.kt           tokenizer: tags, attrs, text, named+numeric entities, void elements,
                                    raw-skip for script/style   ← pure JVM
             HtmlTree.kt            tolerant tree builder for the legacy-parse subset   ← pure JVM
  pipeline/  ArticlePipeline.kt     drop/strip/transform per rendering-exclusions §§1–8 + §1-resolutions above
             DisambigParser.kt      chooser extraction (port of DisambiguationParser semantics)
             SnippetText.kt         search-snippet strip + ellipsis (port)
             ErrorMessages.kt       exception → calm copy (port)
  model/     ArticleDocument.kt     List<Block>: Heading(2..4) · Paragraph(spans) · ListBlock · Blockquote ·
                                    Figure(src,w,h,caption) · InfoboxCard(rows) · SimpleTable(headers,rows) ·
                                    MathImage(src)   ← pure JVM, the renderer's entire input
  ui/        SearchScreen/VM, DisambiguationScreen/VM, ArticleScreen/VM
             render/BlockRenderer.kt   Block → Compose (LightText styles, AnnotatedString spans)
             render/Images.kt          OkHttp fetch → BitmapFactory.decodeStream → ImageBitmap,
                                       saturation-0 ColorFilter (grayscale), bounded in-memory cache,
                                       aspect-ratio placeholder, drop-figure-on-failure
tool/src/test/kotlin/...              parser/, pipeline/, model/ tests — the pure-JVM gate (kotlin.test)
```

The parser is **not** a general HTML5 parser: it is scoped to the tag/attribute/entity repertoire actually observed in `action=parse&prop=text` output, held honest by fixtures (real captured article HTML, checked into `tool/src/test/resources/`). The May tests' fixtures + assertions (61 tests, 01 §3) are the porting substrate. `rendering-exclusions.md` v4 lives in the repo and stays the audit contract; its checklist greps re-point at the new files.

**Storage:** DataStore (via the SDK context) for exactly one value: `textScalePercent: Int`. No Room, no files, nothing else persisted. All caches in-memory, bounded, process-scoped (D11).

**Offline/error:** every network call is wrapped once at the repository seam; failures map through `ErrorMessages`; screens show copy + Retry. No connectivity monitoring (no `ACCESS_NETWORK_STATE` — 02 §1). Images fail soft and silently (figure dropped). Airplane-mode walk of all three screens is a milestone gate.

## 4. Network contract

Base `https://en.wikipedia.org/w/api.php`, `formatversion=2`, GET only (May recipes, wiki `CLAUDE.md` API §1–3):
1. search: `action=query&list=search&srsearch=<q>&srlimit=20`
2. disambiguation: `action=query&prop=pageprops&ppprop=disambiguation&redirects=1&titles=<t>`
3. article: `action=parse&prop=text&redirects=1&page=<t>` (legacy parser output — D5 stands for v1; Parsoid remains a someday question)

Plus image GETs to `upload.wikimedia.org` only (rewritten protocol-relative srcs — `HtmlProcessor.fixImages` semantics). Descriptive User-Agent per Wikimedia etiquette (D9), pointing at the real repo once it exists — never the phantom `…/lightwiki` URL (02 §4.2). No other host, ever; a debug-build assertion enforcing the two-host allowlist at the client seam is cheap and makes the defense claim mechanical.

## 5. Permissions and `lighttool.toml`

**`permissions = ["android.permission.INTERNET"]` — the minimum for a network reader, and the only entry.** Justification (defense-doc line): every byte the tool fetches is the article the user explicitly asked for, from Wikimedia, at tap time. `ACCESS_NETWORK_STATE` is deliberately absent (requested-and-unused is a documented house defect pattern — `lp3-head-to-head.md` punch list #6). Portrait lock via `orientation` (validated against `ALLOWED_ORIENTATIONS = setOf("portrait")`, `plugin/.../LightToolMetadata.kt:145,111–113`) — a reader has no landscape story, and the sibling chess audit showed omitting it ships a rotation bug.

Intended committed file, byte-for-byte:

```toml
[tool]
id            = "dev.tyler.wiki"
label         = "Wiki"
versionCode   = 1
versionName   = "0.1.0"
permissions   = ["android.permission.INTERNET"]
orientation   = "portrait"
serverPackage = "com.lightos"
# serverPackage = "com.thelightphone.sdk.emulator"
```

(`serverPackage` committed as `com.lightos` per house rule + CI gate; the commented emulator line is the local AVD flip, restored with `git checkout -- tool/lighttool.toml`. `versionName` strict semver, no leading zeros — sdk-facts §8.)

## 6. Updated vetting defense — "Why this is a clean tool to vet"

Working draft, mapped point-by-point to the category rules in `references/vetting.md`. **The README rendition is Tyler's to write in his own voice (04 schedules it); this section is the argument's content, kept current from here on.**

> Wiki is a single-purpose Wikipedia reader: type a search, read one article, done. It is a *reference lookup* tool in the Dictionary tool's grammar — and "Wikipedia inside the Dictionary tool" is a standing community request (register Part 6).
>
> - **Not browser-adjacent:** 100% native Compose — no WebView, no embedded browser, no remote HTML execution, no JavaScript anywhere. Article HTML is parsed into a typed document model on-device and drawn with the SDK's own text components. There is no URL bar, no address of any kind, and **no hyperlinks: every link is stripped to plain text before rendering** — the app cannot go where a page points. *(Answers: "Any WebView… is the sharpest line the LP3 draws.")*
> - **Not a feed / not infinite:** the unit of consumption is one article — a finite document; **the screen ends where the article ends.** Nothing follows it: no related articles, no recommendations, no trending, no random-article, no "what's new." There is no browse surface at all — the only entry point is a deliberately typed search capped at 20 results — and no history, bookmarks, notifications, or background refresh exists to pull anyone back. Moving to another article costs a fresh typed search; the disambiguation chooser is the one navigation surface and it is a bounded list that resolves a single ambiguous title, then disappears. *(Answers: "endless scroll, a timeline… checked compulsively.")*
> - **Not messaging/social:** nothing is shared, posted, or seen by anyone else.
> - **Not commercial:** open-source (MIT, inherited from the repo root per portfolio convention), no accounts, no ads, no telemetry, no tips.
> - **Permissions:** `["android.permission.INTERNET"]` — the article fetch is the product; there is no second permission to explain.
> - **Dependencies:** allow-listed only (Compose, Ktor/OkHttp, kotlinx-serialization, coroutines, DataStore, kotlin-test). The HTML parser is ~small, hand-written, in-repo, and fixture-tested — no parsing dependency at all.
> - **Data:** requests go to Wikimedia only — `en.wikipedia.org` (API) and `upload.wikimedia.org` (article images), at explicit user action; nothing is stored beyond a text-size preference; nothing identifies the user beyond a standard descriptive User-Agent.
>
> Finite-by-rule check: every list is bounded (20 results; chooser = the disambiguation page's own finite entries; article = one document), every fetch is user-initiated, and the app holds no state designed to be checked again.

## 7. Draft GitHub issues (drafts only — I do not file; **Light's CONTRIBUTING requires human-written communication** (register Part 7), so treat these as content outlines to rewrite in your own words before filing)

### 7.1 Re-vetting question (portfolio-wide, blocks the backlog's release plan — memory.md open item)

> **Title:** Do approved tools need re-vetting for feature updates?
> **Body points:** Submitting 2–3 tools for the first vetting window; each has a deliberately small v1 and a post-approval backlog. Question: when an approved tool ships a feature update (new `versionCode`/`versionName`, same permissions, same category), does it re-enter the full vetting queue, a lighter re-review, or is the signed rebuild from the public commit sufficient? Asking because it decides whether we hold features back for a fatter v1 or ship the minimal finite version now and iterate. (No new permissions or category changes contemplated; understood those would re-open review.)

### 7.2 jsoup allow-list request (parallel to the build, not gating it — Phase-2 decision)

> **Title:** Allow-list request: `org.jsoup:jsoup` (HTML parsing for reader tools)
> **Body points:** Use case — reader tools that consume real-world HTML (a Wikipedia reader in my case; the reading cluster is the register's second-largest). jsoup is MIT-licensed, mature (2009–), zero transitive dependencies, pure-JVM, no Context/reflection/IO beyond what callers hand it. Without it every reader hand-rolls a partial HTML parser (mine is in progress — shippable, but duplicated effort across the ecosystem). Happy to PR the one-line `ALLOWED_DEPENDENCIES` addition if green-lit per CONTRIBUTING.
> **Note in issue:** not blocking my submission; requesting for the ecosystem.

If accepted later, swapping the substrate back under `ArticlePipeline` is contained by design (lexer/tree behind one seam).

## 8. Open items resolved by 04

Milestones/DoD/verification tiers; review placement; subagent split; documentation schedule (repo `CLAUDE.md`, `00-ASSESSMENT.md`, `tool/README.md` + screenshots, `SUBMISSION.md`); submission checklist instantiation; risks; and the short list of decisions that remain Tyler's (id/label confirmation above all — it is permanent).
