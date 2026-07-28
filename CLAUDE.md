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
- `~/Documents/_archive/lightos/lightwiki` (May 2026 Phase-1 app) is a **read-only donor** — pipeline semantics, test fixtures/assertions, `rendering-exclusions.md`, `article.css` values. Never modify it.
- `lightphone/*` is strictly read-only — no pushes, issues, or PRs; all upstream communication is Tyler's, human-written.
- Tokens/credentials never inline in commands, output, or files — hand the command to Tyler.
- Don't use Claude Design or its "Handoff to Claude Code" export (emits React, not Compose) — standing house rule.

## Implementation notes

### M1 (2026-07-28)

- Phase-0 re-grep: all 13 verified facts re-checked against this repo's vendored `sdk/`/`plugin/` (fork pins audit commit `d2323e3`) — every line citation matched; no corrections. Weather example's on-disk package is `com/thelightphone/weather/` (fact 10's `examples/weather/WeatherApi.kt` shorthand stands). Scan walker confirmed filtering `extension == "kt"` (`LightSdkPlugin.kt:367`) — fixture content in `resources/` is unscanned, as assumed.
- Fixtures (subagent, report: `.superpowers/task-reports/m1-fixture-harvest.md`): 4 API JSON envelopes + 12 article HTML bodies (~5.4 MiB) in `tool/src/test/resources/fixtures/`, attributed CC BY-SA 4.0 in `fixtures/README.md`. Scan-vet against both `LightSdkPlugin` lists (16 imports as fixed strings, 29 patterns as PCRE): all pass, zero swaps. Disambiguation polarity captured both ways in `pageprops-*.json`. Redirects documented (Mercury (disambiguation)→Mercury; UN population list title drift).
- Port (subagent, report: `.superpowers/task-reports/m1-port-snippet-errors.md`): `SnippetText`/`ErrorMessages` + tests → `dev.tyler.wiki.pipeline`, bodies byte-identical to donor. **Zero assertion re-orderings needed** — the donor's 22 `assertEquals` are all 2-arg; the message-last trap (§facts 11) is live for M3's ported suites, not these. `ErrorMessages` maps only JDK exception types (IOException, SocketTimeoutException, UnknownHostException) — no Retrofit coupling existed. 16/16 green under `:tool:testDebugUnitTest` (12+4), re-verified in main loop after the subagent run.

### M0 (2026-07-28)

- Scaffolded via `new-light-tool` skill: identity commit `7264478` on branch `wiki` in the light-sdk clone → `create-tool-repo.sh` → this repo, branch protection with `check / check` + `submission-check / submission-check`, four caller workflows.
- Identity `dev.tyler.wiki` / `Wiki` Tyler-confirmed before scaffold.
- M0 scaffold-conformance review (fresh-context subagent) passed all four checks: toml byte-identical to plan + CLAUDE.md copy in sync; exactly the four thin caller workflows; no branch commit touches sdk/; doc citations spot-verified against the vendored SDK. Three minor findings, all resolved in the PR: (1) `upstream/main..HEAD` shows two `sdk/ui` drawables — upstream's `fix/toggle-icons` merged the day after our fork point `d2323e3`, not our edit; clears with the first upstream sync. (2) docs/04 had a stale milestone number ("M5 spike" for the image spike, which lives in M6) — corrected. (3) SUBMISSION.md's testing-notes line pre-asserted QA as fact; now marked TODO so the M8 sweep must close it.
