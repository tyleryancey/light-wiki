# CLAUDE.md — Wiki (Light Phone 3 tool)

A search-only, link-stripped, native-Compose Wikipedia reader: type a search, pick a result, read one finite article, put the phone away. Targeting the Aug–Sep 2026 Tool Library vetting window.

**Division of labor:** this doc is the plan of record; Claude Code owns compile–run–debug. **SDK source outranks this doc** — when they disagree, source wins and the doc gets corrected in the same change. Full spec: `docs/03-v1-spec.md` (frozen scope); this doc supersedes the `_cowork/light-wiki-revival/04-implementation-plan.md` handoff from M0 onward.

## Purpose

Reference lookup in the Dictionary tool's grammar — "Wikipedia inside the Dictionary tool" is a standing community request (tool register Part 6). No feed, no links, no history: the unit of consumption is one article, fetched at explicit user action, and the screen ends where the article ends.

## Verified SDK facts this tool relies on

Verified 2026-07-28 against `light-sdk` @ `d2323e3` (this repo's vendored copy is the law here — M1 re-greps every item against it). Re-grep before trusting.

1. **Screen framework:** `abstract class LightScreen<ResultType, VM : LightViewModel<ResultType>>` (`sdk/client/src/main/kotlin/com/thelightphone/sdk/LightScreen.kt:51`), `SimpleLightScreen` (`:11`), `LightViewModel<T>` (`LightViewModel.kt:6`), `navigateTo(screenFactory) { result }` (`LightScreen.kt:40`), `goBack(result)` (`:45`). Exactly one `@InitialScreen` (`InitialScreen.kt:8`) and one `@EntryPoint object : LightEntryPoint` (`EntryPoint.kt:10–12`) app-wide — KSP enforces.
2. **Context surface:** `SealedLightContext` exposes exactly `dataStore` / `filesDir` / `fileShare` (`sdk/client/src/main/kotlin/com/thelightphone/sdk/LightActivity.kt:223–226`). Text-size pref lives in that `dataStore`.
3. **Typed input:** `LightTextInputEditor` (two overloads, `sdk/ui/.../LightTextInputEditor.kt:46,95`) is a **full-screen** editor driven by `rememberTextFieldState()` + a `keyboardOptionsFlow`; `LightTextField` is read-only display that opens it. Canonical usage: `examples/weather`.
4. **Theme/layout:** `LightThemeTokens` (`sdk/ui/.../LightTheme.kt:163`) — read every color from it; `LightGrid` is a constants object (`WIDTH=27/HEIGHT=31`, `LightGrid.kt:14–15`) with `Float.gridUnitsAsDp()` (`:19`) — size in grid units; there is no LightGrid container composable.
5. **Dependency allow-list** (`LightSdkPlugin.kt`, `ALLOWED_DEPENDENCIES`): includes `androidx.compose`, `androidx.activity:activity-compose`, `org.jetbrains.kotlinx:kotlinx-coroutines`, `androidx.lifecycle`, `androidx.datastore`, `com.squareup.okhttp3:okhttp`, `io.ktor`, `org.jetbrains.kotlinx:kotlinx-serialization`, `org.jetbrains.kotlin:kotlin-test`, `androidx.media3`. Matching is `startsWith` on `group:name`; transitives of an allowed dep pass. **Not present: jsoup, retrofit, coil, anything image-loading.**
6. **Permissions & orientation** (`LightToolMetadata.kt`): `ALLOWED_PERMISSIONS` includes `android.permission.INTERNET`; `ALLOWED_ORIENTATIONS = setOf("portrait")` (`:145`, validated at `:111–113`) — `orientation = "portrait"` is legal toml.
7. **Plugin scan semantics** (`LightSdkPlugin.kt`): runs at Gradle **configure** time; walks **all of `tool/src/`, tests included**; per-line, split on `;`; only statements *beginning* with a comment marker are skipped — **banned tokens inside string literals or trailing comments still fail**. `.java` files rejected outright. Blocked imports include `androidx.activity.`, `android.content.Context`, `LocalContext`; blocked patterns are API-shaped (`getSystemService(`, `startActivity(`, `contentResolver`, reflection forms).
8. **`android.webkit` appears nowhere in the scan lists** — do not read that as permission. WebView is banned at *vetting* level (Light: "off the table… sandboxing concerns" — tool register Part 7) and at house level. The gate is the human reviewer; never reason from scan gaps.
9. **`android.graphics` is not scan-blocked**, and `BitmapFactory` is **used nowhere in `sdk/` or `examples/`** — the image path in this tool is a first for the platform. Treat as unproven until the M6 spike passes on the AVD.
10. **Networking:** Ktor `3.4.2` (`gradle/libs.versions.toml:6`); `examples/weather/WeatherApi.kt:3–7` uses `ktor-client` with the **OkHttp engine** + content-negotiation — copy that stack.
11. **Tests:** `kotlin.test` only; **message is the LAST argument** (`assertEquals(expected, actual, message)`) — reverse of the May donor repo's JUnit4. Every ported assertion gets its arguments re-ordered.
12. **Toolchain:** JDK 17, minSdk 33, compile/target 36, Gradle 9 / AGP 8.12.3 / Kotlin 2.3.20; `:tool:clean` must be a **separate invocation** from build tasks; GitHub Packages creds are `gpr.user`/`gpr.key` in `local.properties` or `GH_PACKAGES_USER`/`GH_PACKAGES_TOKEN` env (README's names are wrong).
13. **Physical-LP3 reality (ringtone-studio precedent, verified on TLP301 2026-07-25):** unvetted sideloads run fine for UI + input (the real LP3 keyboard worked); only token-gated server RPCs fail pre-vetting (`NoPermission`). Wiki calls no `LightServiceMethod` RPC at all — nothing here is gated on approval. AVD trap: fresh emulator defaults Settings → Allowed Tools to "Community Tools"; set **"All Tools"** once per AVD.

## lighttool.toml

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

`id`/`label` Tyler-confirmed 2026-07-28 (id is permanent). AVD work flips `serverPackage` locally; restore with `git checkout -- tool/lighttool.toml` before committing — CI fails any PR whose committed value isn't `com.lightos`.

## Architecture

```
tool/src/main/kotlin/dev/tyler/wiki/
  ToolEntryPoint.kt                 @EntryPoint, empty hooks
  data/      WikiApi.kt             Ktor client (OkHttp engine), 3 endpoints (search / pageprops / parse),
                                    kotlinx-serialization DTOs, UA "LightWiki/<version> (+repo URL)"
             WikiRepository.kt      bounded LRUs (search 32 / disambig 64 / article-model 16), suspend facade
  parser/    HtmlLexer.kt           tokenizer: tags, attrs, text, named+numeric entities, void elements,
                                    raw-skip for script/style   ← pure JVM
             HtmlTree.kt            tolerant tree builder for the legacy-parse subset   ← pure JVM
  pipeline/  ArticlePipeline.kt     drop/strip/transform per rendering-exclusions §§1–8
             DisambigParser.kt      chooser extraction (port of May DisambiguationParser semantics)
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
tool/src/test/resources/fixtures/     captured API JSON + article HTML, attributed (CC BY-SA), scan-vetted
```

Two structural rules that govern every milestone:

- **Pure-JVM gate:** `parser/`, `pipeline/`, `model/`, plus `SnippetText`/`ErrorMessages`, import nothing `android.*`/`androidx.*` and are fully green under `:tool:testDebugUnitTest` **before any screen exists**.
- **Renderer seam survives:** `ArticleDocument → Compose` mapping lives in `ui/render/`; nothing above it knows about Compose.

**Network contract:** base `https://en.wikipedia.org/w/api.php`, `formatversion=2`, GET only — (1) `action=query&list=search&srsearch=<q>&srlimit=20`; (2) `action=query&prop=pageprops&ppprop=disambiguation&redirects=1&titles=<t>`; (3) `action=parse&prop=text&redirects=1&page=<t>`. Plus image GETs to `upload.wikimedia.org` only. No other host, ever; a debug-build assertion enforces the two-host allowlist at the client seam.

**Storage:** DataStore (SDK context) for exactly one value: `textScalePercent: Int`. No Room, no files, nothing else persisted. All caches in-memory, bounded, process-scoped.

## Behavior

1. **SearchScreen** (`@InitialScreen`) — `LightTopBar` "Wiki"; `LightTextField` (read-only display) opens `LightTextInputEditor` (full-screen, weather pattern). Submit → ≤20 result rows (title + snippet). States: idle (hint copy), loading, results, empty ("No articles found."), error+Retry.
2. **DisambiguationScreen** — chooser list (title + description); tap → ArticleScreen. Bounded; the app's only navigation surface.
3. **ArticleScreen** — `LightTopBar` ellipsized title; `LazyColumn` of rendered blocks; bottom bar small-A/large-A `LightBarButton`s (text scale 80–180 step 10, default 110, persisted; no reload, no scroll jump). Back pops the article stack (chooser round-trips preserved), then Search.

Flow: Search → submit → if disambiguation → chooser → article; else article directly. Process death → Search (by design: no history, in-memory back stack).

**Never in this tool** (locked; scope change requires a plan amendment): in-article links · history · bookmarks · related/recommended anything · random-article · notifications · background refresh · analytics/telemetry · third-party calls · accounts · tip-jar.

## Milestones · definitions of done

Full task breakdown: `docs/04-implementation-plan.md`. Every milestone ends with a fresh-context code review; findings logged in Implementation notes below.

- **M0 — Scaffold, identity, CI green** *(tier: CI)*. DoD: public repo, protected `main`, green first PR, plan-of-record docs merged.
- **M1 — Phase-0 verification + fixture harvest** *(JVM)*. Re-grep verified facts against this repo's vendored `plugin/`/`sdk/`; harvest 10–15 fixtures (incl. `Mercury (element)`, disambig page, infobox bio, math, wide/color tables, stub) with attribution; scan-vet fixtures; port `SnippetText` + `ErrorMessages` + 16 tests. DoD: fixtures committed + attributed + scan-clean; 16 ported tests green.
- **M2 — Parser substrate** *(JVM — the risk pocket, gated)*. `HtmlLexer` + tolerant `HtmlTree`. **Kill criterion: ~3 focused days without fixtures passing → stop, fall back (jsoup issue / simpler source).** DoD: all fixtures parse; suite green; zero Android imports.
- **M3 — Pipeline + document model** *(JVM)*. Port May pipeline semantics + DisambigParser; build `ArticleDocument` extraction; port May assertions (28+12+4) onto new substrate; `rendering-exclusions.md` v4. DoD: ≥61 tests green, pure-JVM gate closed — UI may now begin.
- **M4 — Data layer + Search & chooser screens** *(AVD)*. `WikiApi`/`WikiRepository` + JVM tests; three screens skeleton + navigation; AVD walkthrough of search states + disambig round-trip. DoD: live search-to-chooser on AVD; zero color literals.
- **M5 — Article renderer: text** *(AVD + JVM)*. `BlockRenderer` with May `article.css` typography mapping; A/A text scale persisted, no reload/scroll-jump. DoD: full text-only article reads end-to-end with working A/A.
- **M6 — Images, infobox, tables, math** *(AVD)*. **Opens with the ½-day image spike (unprecedented path — §facts 9); if it fails for a sandbox reason: stop, record, retreat to text-only v1 (Tyler confirms).** Bounded bitmap LRU, downsampling, drop-figure-on-failure, grayscale ColorFilter. DoD: v1 block set renders; image failure modes verified airplane-mode.
- **M7 — Hardening + physical LP3** *(AVD + LP3)*. Error/offline sweep; perf; monochrome audit; `workspace-health`; sideload pass on hardware; screenshot harvest. DoD: every spec §2 state demonstrated on hardware.
- **M8 — Submission package** *(checklist + human)*. `SUBMISSION.md` (description = Tyler), `tool/README.md` (defense prose = Tyler), pre-submission full review, `release-tool` tag, Tyler files the submission (human-written, per Light's CONTRIBUTING).

## Vetting defense (seed)

Wiki is a single-purpose reference tool. **Not browser-adjacent:** 100% native Compose — no WebView, no JavaScript, no URL bar; every link is stripped to plain text before rendering, so the app cannot go where a page points. **Not a feed:** one finite article per deliberately typed search (≤20 results); no related/trending/random, no history, no notifications — nothing pulls anyone back. **Not commercial/social:** open-source (MIT), no accounts, no ads, no telemetry, nothing shared. **Permissions:** `["android.permission.INTERNET"]` only — the article fetch is the product. **Data:** Wikimedia hosts only (`en.wikipedia.org`, `upload.wikimedia.org`), at explicit user action; nothing stored beyond a text-size preference. Finite-by-rule: every list bounded, every fetch user-initiated, no state designed to be checked again. (README rendition is Tyler's to write — M8.)

## Sharp edges

- Gradle needs GitHub Packages credentials as `GH_PACKAGES_USER`/`GH_PACKAGES_TOKEN` env vars, or `gpr.user`/`gpr.key` in `local.properties` — the SDK README's own property names for these are wrong.
- `serverPackage` must stay `com.lightos` in commits: Light's builder compiles the committed value, so an emulator value produces an APK that cannot bind to LightOS on real hardware. Flip it locally for AVD work, then restore with `git checkout -- tool/lighttool.toml` before committing.
- `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` before any `./gradlew`, every shell.
- The plugin scan walks `tool/src/` including test `.kt` files; string literals are **not** exempt. Fixture *content* lives in `resources/` (not scanned — the walker filters `*.kt`), but any fixture snippet pasted into a `.kt` test must be vetted against `BLOCKED_CODE_PATTERNS` first.
- `assertEquals(expected, actual, message)` — message LAST. Porting JUnit4 means re-ordering; a silently-swapped expected/actual still compiles.
- `:tool:clean` separately; never `clean assembleDebug` combined (generated manifest race).
- AVD: Settings → Allowed Tools → "All Tools" once per fresh emulator.
- Every `gh` call names `-R tyleryancey/light-wiki` explicitly (the `upstream` remote can silently retarget `gh` at read-only `lightphone/light-sdk`).
- Merge PRs with `--merge`, never squash/rebase — squash breaks future upstream syncs.
- No hand-written `AndroidManifest.xml`; no `applicationId`/`versionCode`/`versionName`/`namespace` in build scripts — all from `lighttool.toml`.
- `LightTextField` cannot take typing; `LightTextInputEditor` is a full-screen destination, not an inline widget — lay out Search around that.
- SDK back stack + VMs are in-memory; process death → Search screen by design (no restoration work).
- Wikipedia HTML shifts (the May code already handles two heading shapes). Fixtures pin today's shape; a live-fetch smoke test in M7 catches drift before submission.
- `HtmlTree.parse` is fully iterative and survives adversarially deep trees (100 k nesting builds in ~9 ms), but `HtmlNode.Element` is a data class — `equals`/`hashCode`/`toString` recurse through children, and any recursive walker will StackOverflow on such a tree. M3+ pipeline walkers must iterate (explicit stack) or bound depth; never call `==`/`toString()` on untrusted whole trees outside tests.
- `~/Documents/_archive/lightos/lightwiki` (May 2026 Phase-1 app) is a **read-only donor** — pipeline semantics, test fixtures/assertions, `rendering-exclusions.md`, `article.css` values. Never modify it.
- `lightphone/*` is strictly read-only — no pushes, issues, or PRs; all upstream communication is Tyler's, human-written.
- Tokens/credentials never inline in commands, output, or files — hand the command to Tyler.
- Don't use Claude Design or its "Handoff to Claude Code" export (emits React, not Compose) — standing house rule.

## Implementation notes

### M5 (2026-07-28)

- `ui/render/ArticleTypography.kt` (pure JVM, TDD): May `article.css` hierarchy as data — base 18sp/1.6, headings 1.25 lh with h2 1.35em · h3 1.15em · h4 1.02em, lead 1.05em, captions 0.85em; A/A scale 80–180 step 10 default 110 with clamped step functions; lead = first Paragraph block. 5 tests.
- `ui/render/BlockRenderer.kt`: LazyColumn over blocks (keyed by index); AnnotatedString bold/italic spans; h2 hairline from `contentSecondary` via alpha (no color literals); list markers ("–" / "n."), level-2 indent; blockquote left-bar inset (IntrinsicSize.Min). M5 renders text blocks only; Figure/InfoboxCard/SimpleTable/MathImage skipped silently until M6. Styles derive from `LightThemeTokens.typography.paragraph` copies (token font family) with sizes from ArticleTypography.
- `ArticleScreen`: BlockRenderer body + `LightBottomBar` small-a/large-A `LightBarButton.Text`; `textScalePercent` in `SealedLightContext.dataStore` (`intPreferencesKey("textScalePercent")`); scale updates state first, persists async — **no reload, no scroll jump** (verified on AVD: position held across two A-steps).
- AVD pass (screenshots in scratchpad): Mercury (element) reads end-to-end — bold lead spans, "Properties" h2 with hairline + "Physical properties" h3 scale; smooth deep scroll; killstart lands on Search (by design); scale persisted across process death (fresh article at 130%).
- Suite 137 green.
- M5 review (fresh-context) found 4, all addressed: (1) IMPORTANT — the ordered-list marker gutter was a fixed grid unit while text scales in sp, so `"10."` wrapped at every scale — root cause shared with (3): the May css is em-based *including spacing* ("sizes stay relative"), so all block spacing (paragraph/heading margins, list indent/item spacing, marker gutter min-width, blockquote inset) is now em-derived via `ArticleTypography` constants and an sp→dp helper — the whole page scales together; (2) blockquote bar now `content` (css `--ink`), not the muted token; (4b) A/A persist moved to a process-scoped `WikiGraph.appScope` — backing out during the write window no longer drops the pref (viewModelScope is cancelled on pop, per `LightScreen.kt:69–72`); 4a (one-frame bounce on rapid taps, self-healing) accepted as cosmetic. Review verified clean: typography constants vs donor css, `key = index` scroll stability, Dispatchers.Default hygiene, monochrome (0 literals), DataStore singleton safety across stacked screens.

### M4 (2026-07-28)

- Data layer TDD: DTOs decoded against the real captured fixture JSON (search 20 hits; pageprops disambiguation polarity both ways; parse envelope); `WikiHosts.assertAllowed` (https + two-host, tested incl. `en.wikipedia.org.evil.com` and plain http rejections) runs on every `KtorWikiApi` request; `WikiRepository` LRUs 32/64/16 cache *parsed models* (verified: 1 api call per 2 reads for search/sections/article), parsing on `Dispatchers.Default`. UA `LightWiki/0.1 (+https://github.com/tyleryancey/light-wiki)`. The 3 DTO tests validated against fixtures rather than failing first (declarative shape); the 6 behavior tests were watched RED.
- Screens: sample tool code deleted; `ToolEntryPoint` (empty hooks), `SearchScreen` (`@InitialScreen`, mode-machine: Idle/Input/Loading/Results/Empty/Error; weather's `LightTextInputEditor` session pattern; VM emits `NavTarget`, `Content` consumes via `LaunchedEffect` then `navigateTo`), `DisambiguationScreen(title)` chooser, `ArticleScreen(title)` M5 stub showing live block count. `WikiGraph` service locator. Nothing uses `onScreenShow` for state (re-fire trap avoided by construction).
- AVD walkthrough (emulator-5554, all screenshots in scratchpad): idle → editor+LP3 keyboard → live "mercury" results with cleaned snippets/ellipsis → chooser (pre-heading entries + "Companies" section, DisambigParser live) → Mercury (element) stub **140 blocks through the real pipeline** → back returns to chooser → airplane-mode error shows ported friendly copy + RETRY → recovery to live results → empty state ("No articles found."). `serverPackage` flipped locally and restored before commit.
- The input editor prefills with the current query per session (`remember(inputSession) { TextFieldState(query) }`) — survives screen recreation, unlike a single remembered state.
- Idiom audits: `Color(` 0 · raw `.dp` 0 (grid units only) · blocked imports 0.
- M4 review (fresh-context; SDK-lifecycle claims verified from `LightScreen.kt:57–77`/`LightActivity.kt:64–87`) found 5, all fixed: (1) IMPORTANT — MediaWiki 200-with-`{"error":...}` envelopes decoded to null payload keys and were cached as empty results (sticky blank article / "No articles found" with no retry) — repository now throws `IOException` on absent payload keys (never cached, routes to Error+RETRY), failing tests first; (2) `select()` in-flight guard + `openInput` clears `navTarget` (double-select and editor-yank races); (3) `settled`-mode tracking fixes stale-results-after-error and stale-query-after-empty-submit edges; (4) editor prefill made real (above) — the earlier "retains text like weather" claim was overbroad (state died on navigation); (5) allowlist tests pin the userinfo trick (`en.wikipedia.org@evil.com`) and case-variant hosts (impl was already fail-closed). Review confirmed: per-screen `ViewModelStore` cleared on pop (so `init{}` loads run once per pushed instance); `onScreenShow` re-fires on pop-back but no wiki VM overrides it; `navConsumed`-then-`navigateTo` is atomic (no suspension point). Suite 132 green; sanity-driven on AVD after fixes.

### M3 (2026-07-28)

- **Pure-JVM gate closed — UI may now begin.** 116 tests green (`:tool:testDebugUnitTest`), 0 android imports under `dev/tyler/wiki`; May target of ≥61 nearly doubled.
- `ArticlePipeline` (tree→tree, immutable depth-capped rebuilds ≤256): May pass order preserved (dropAppendix → stripLinks → fixImages → stripClutter → reflowInfobox). 03 §1 resolutions applied: galleries in the clutter list (res.4); `<style>`/TemplateStyles structurally moot (lexer raw-skips); **images KEEP width/height** (res.8 reverses May). Infobox reflow is a single-pass rebuild — removal+insertion must share one traversal because rebuilding invalidates reference identity (caught pre-test).
- 26 of May's 28 `HtmlProcessorTest` re-expressed against the tree (27 with the new gallery test); retired: stylesheet-injection + viewport-meta (WebView-era, no counterpart); dimension-strip test asserts the reversed keep-dimensions contract.
- `DisambigParser` ported onto raw tree (12/12 May tests), chrome skipped both at container level and inside entries; `LruCache` + 4 tests ported byte-identical to `data/`.
- `ArticleDocument`: closed block vocabulary (Heading 2–4 · Paragraph(spans) · ListBlock(≤2 levels, deeper flattened) · Blockquote · Figure(src,w,h,caption) · InfoboxCard · SimpleTable · MathImage); iterative inline-span extraction with bold/italic state; inline math contributes `alt` text; unknowns drop silently; transparent containers (`div section center main article dl dd`) recurse (cap 64). 19 unit + 6 fixture-extraction gates (Mercury infobox-after-lead, Fourier MathImages, 100+-row population table, Marie Curie card ≥5 rows, stub, Gettysburg blockquotes).
- `rendering-exclusions.md` v4 committed at repo root: §§1–8 re-cited to new files, v3 §5/§6 (CSS/WebView) superseded structurally, §10 records the 03 §1 resolutions; audit greps re-pointed.
- M3 review (fresh-context, both-directions contract audit) found 5: (1) IMPORTANT — nested-list flattening emitted sibling sub-runs out of document order (visible on outline-of-chemistry, 16 lists at depth ≥3) — fixed with document-order recursion (depth-capped), failing test first; (2) disambig chrome skipped only at container level, so legacy-shape headings leaked `[edit]` and descriptions leaked chrome text — fixed by chrome-aware `flatText` + chrome-classed `<li>` skip, plus a real mercury-disambiguation fixture gate (16 sections/115 entries); (3) NBSP trim in description is a port *addition*, not donor behavior (donor had a duplicate plain space where NBSP was evidently intended) — kept, documented in v4 §8; (4) `script` was a one-sided RAW_SKIP_TAGS entry in v4 §5 — doc fixed; (5) double space at style boundaries — boundary dedupe in `mergeAndClean`, test first. Contract audit otherwise bidirectionally exact (all six lists). Suite now 121/0.

### M2 (2026-07-28)

- Parser substrate built TDD (red-green-refactor batches, all failures watched first): `HtmlToken`/`HtmlLexer` (single-pass char scanner; named+numeric entities decoded once, in text and attr values; comments/doctype/PI swallowed; `<script>`/`<style>` swallowed whole; truncated tags become text; tag names may contain `-`), `HtmlNode`/`HtmlTree` (stack builder; HTML5 void set; `p/li/dt/dd/tr/td/th/option` self-nesting auto-close; mismatched end tags close through; stray end tags ignored; EOF auto-close; synthetic `#root`). 25 unit tests + 3-test fixture gate at review time (see below for post-review count).
- **Fixture gate passed on first integration run**: all 12 harvested articles lex+build; largest (fourier-transform, 1.6 MB) parses in ~0.3 s incl. suite overhead; no raw entities survive decoding. The M2 kill criterion (3 days) was never approached — MediaWiki legacy-parse output is well-formed enough that tolerance is a safety net, not a crutch.
- Pure-JVM gate holds: `grep -rn "^import android"` over `tool/src/**/dev/tyler/wiki` → 0.
- Two lexer tests passed without failing first (bare-`&` and stray-`<` tolerance) — they document behaviors batch 1 already provided and guard the entity decoder; noted per TDD discipline.
- M2 fuzz review (fresh-context subagent) found and measured **two adversarial O(n²) paths**, both unreachable from well-formed MediaWiki output (fixtures structurally can't catch them): (1) unbounded `indexOf(';')` in entity decoding — 6.5 s on a 640 KB `&`-flood; (2) full-stack `indexOfLast` per unmatched end tag — 13.5 s at 80 k stray end tags. Both fixed with failing scaling probes first (bounded entity scan window; per-name open-count map for O(1) stray-end-tag rejection); probes stay in the suite as regression guards (<2 s bounds). Also fixed: spaced `=` attribute tolerance, `classes` splitting on any ASCII whitespace. Review's no-throw fuzz batch (numeric overflow, EOF truncations, NULs, nameless attrs, unterminated raw-skip) all passed unchanged. Suite (from JUnit XML): 29 parser unit (16 lexer + 13 tree) + 3 fixture-gate + 16 pipeline = 48.

### M1 (2026-07-28)

- Phase-0 re-grep: all 13 verified facts re-checked against this repo's vendored `sdk/`/`plugin/` (fork pins audit commit `d2323e3`) — every line citation matched; no corrections. Weather example's on-disk package is `com/thelightphone/weather/` (fact 10's `examples/weather/WeatherApi.kt` shorthand stands). Scan walker confirmed filtering `extension == "kt"` (`LightSdkPlugin.kt:367`) — fixture content in `resources/` is unscanned, as assumed.
- Fixtures (subagent, report: `.superpowers/task-reports/m1-fixture-harvest.md`): 4 API JSON envelopes + 12 article HTML bodies (~5.4 MiB) in `tool/src/test/resources/fixtures/`, attributed CC BY-SA 4.0 in `fixtures/README.md`. Scan-vet against both `LightSdkPlugin` lists (16 imports as fixed strings, 29 patterns as PCRE): all pass, zero swaps. Disambiguation polarity captured both ways in `pageprops-*.json`. Redirects documented (Mercury (disambiguation)→Mercury; UN population list title drift).
- Port (subagent, report: `.superpowers/task-reports/m1-port-snippet-errors.md`): `SnippetText`/`ErrorMessages` + tests → `dev.tyler.wiki.pipeline`, bodies byte-identical to donor. **Zero assertion re-orderings needed** — the donor's 21 `assertEquals` call sites (17+4) are all 2-arg; the message-last trap (§facts 11) is live for M3's ported suites, not these. `ErrorMessages` maps only JDK exception types (IOException, SocketTimeoutException, UnknownHostException) — no Retrofit coupling existed. 16/16 green under `:tool:testDebugUnitTest` (12+4), re-verified in main loop after the subagent run.

### M0 (2026-07-28)

- Scaffolded via `new-light-tool` skill: identity commit `7264478` on branch `wiki` in the light-sdk clone → `create-tool-repo.sh` → this repo, branch protection with `check / check` + `submission-check / submission-check`, four caller workflows.
- Identity `dev.tyler.wiki` / `Wiki` Tyler-confirmed before scaffold.
- M0 scaffold-conformance review (fresh-context subagent) passed all four checks: toml byte-identical to plan + CLAUDE.md copy in sync; exactly the four thin caller workflows; no branch commit touches sdk/; doc citations spot-verified against the vendored SDK. Three minor findings, all resolved in the PR: (1) `upstream/main..HEAD` shows two `sdk/ui` drawables — upstream's `fix/toggle-icons` merged the day after our fork point `d2323e3`, not our edit; clears with the first upstream sync. (2) docs/04 had a stale milestone number ("M5 spike" for the image spike, which lives in M6) — corrected. (3) SUBMISSION.md's testing-notes line pre-asserted QA as fact; now marked TODO so the M8 sweep must close it.
