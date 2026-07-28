# 04 — Light Wiki: implementation and execution plan

**Handoff doc for a fresh Claude Code session.** Written in the house CLAUDE.md format; self-contained — assumes nothing from the session that produced it except this file, `03-v1-spec.md` beside it, and the machine's standing layout (below). This becomes the seed of the new repo's `CLAUDE.md` at M0 and is superseded by it from then on.

Light Wiki v1: a search-only, link-stripped, native-Compose Wikipedia reader for the Light Phone 3, targeting the **Aug–Sep 2026 Tool Library vetting window**. Spec (frozen scope, screens, pipeline, defense): `03-v1-spec.md` in this folder — read it first.

**Division of labor:** this doc + 03 are the plan of record; Claude Code owns compile–run–debug. **SDK source outranks every document, including this one** — when they disagree, source wins and the doc gets corrected in the same change.

## Standing layout this plan assumes (verify at session start)

- `~/Documents/lightphone/light-sdk` — clone of `tyleryancey/light-sdk` mirroring upstream; staging for `new-light-tool`. Never commit to its `main`; `lightphone/*` is strictly read-only (no pushes/issues/PRs — CONTRIBUTING bars AI-generated contributions; all upstream communication is Tyler's, human-written).
- `~/Documents/lightphone/light-workspace` — house CI, templates (`ci/templates/`), docs (`docs/README-CHECKLIST.md`, `docs/SYNCING.md`), skills.
- `~/.claude/skills/{new-light-tool,run-light-tool,release-tool,sync-resolve,workspace-health}` — symlinked house skills; use them, don't reimplement.
- **`~/Documents/_archive/lightos/lightwiki`** — the May 2026 Phase-1 app (WebView-era; 21 commits; 61 green JVM tests). **Read-only donor**: pipeline semantics, test fixtures/assertions, `rendering-exclusions.md`, `article.css` typography values. Never modify it.
- `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` before any `./gradlew`, **every shell**.

## Verified SDK facts this plan relies on

Each verified 2026-07-28 against clone `light-sdk` @ `d2323e3` (upstream `89f2675` cross-checked via `gh api` where noted). Re-grep before trusting; commands given where non-obvious.

1. **Screen framework:** `abstract class LightScreen<ResultType, VM : LightViewModel<ResultType>>` (`sdk/client/src/main/kotlin/com/thelightphone/sdk/LightScreen.kt:51`), `SimpleLightScreen` (`:11`), `LightViewModel<T>` (`LightViewModel.kt:6`), `navigateTo(screenFactory) { result }` (`LightScreen.kt:40`), `goBack(result)` (`:45`). Exactly one `@InitialScreen` (`InitialScreen.kt:8`) and one `@EntryPoint object : LightEntryPoint` (`EntryPoint.kt:10–12`) app-wide — KSP enforces.
2. **Context surface:** `SealedLightContext` exposes exactly `dataStore` / `filesDir` / `fileShare` (`sdk/client/src/main/kotlin/com/thelightphone/sdk/LightActivity.kt:223–226`). Text-size pref lives in that `dataStore`.
3. **Typed input:** `LightTextInputEditor` (two overloads, `sdk/ui/.../LightTextInputEditor.kt:46,95`) is a **full-screen** editor driven by `rememberTextFieldState()` + a `keyboardOptionsFlow`; `LightTextField` is read-only display that opens it. Canonical usage: `examples/weather`.
4. **Theme/layout:** `LightThemeTokens` (`sdk/ui/.../LightTheme.kt:163`) — read every color from it; `LightGrid` is a constants object (`WIDTH=27/HEIGHT=31`, `LightGrid.kt:14–15`) with `Float.gridUnitsAsDp()` (`:19`) — size in grid units; there is no LightGrid container composable.
5. **Dependency allow-list** (`LightSdkPlugin.kt`, `ALLOWED_DEPENDENCIES`): includes `androidx.compose`, `androidx.activity:activity-compose`, `org.jetbrains.kotlinx:kotlinx-coroutines`, `androidx.lifecycle`, `androidx.datastore`, `com.squareup.okhttp3:okhttp`, `io.ktor`, `org.jetbrains.kotlinx:kotlinx-serialization`, `org.jetbrains.kotlin:kotlin-test`, `androidx.media3` (`:36` — newer than the lp3-tool-dev sdk-facts sheet, which lacks media3; that sheet needs the correction). Matching is `startsWith` on `group:name`; transitives of an allowed dep pass. **Not present: jsoup, retrofit, coil, anything image-loading.**
6. **Permissions & orientation** (`LightToolMetadata.kt`): `ALLOWED_PERMISSIONS` includes `android.permission.INTERNET` (upstream re-verified, incl. `READ_MEDIA_AUDIO`/`NFC`); `ALLOWED_ORIENTATIONS = setOf("portrait")` (`:145`, validated at `:111–113`) — `orientation = "portrait"` is legal toml.
7. **Plugin scan semantics** (`LightSdkPlugin.kt`): runs at Gradle **configure** time; walks **all of `tool/src/`, tests included**; per-line, split on `;`; only statements *beginning* with a comment marker are skipped — **banned tokens inside string literals or trailing comments still fail**. `.java` files rejected outright. Blocked imports include `androidx.activity.`, `android.content.Context`, `LocalContext`; blocked patterns are API-shaped (`getSystemService(`, `startActivity(`, `contentResolver`, reflection forms) — full lists in the skill's sdk-facts §3–4, spot-verified this audit.
8. **`android.webkit` appears nowhere in the scan lists** (grep count 0 in `LightSdkPlugin.kt`) — do not read that as permission. WebView is banned at *vetting* level (Light: "off the table… sandboxing concerns" — tool register Part 7) and at house level. The gate for this project is the human reviewer; never reason from scan gaps.
9. **`android.graphics` is not scan-blocked** (no entry in `LightSdkPlugin.kt`), and `BitmapFactory` is **used nowhere in `sdk/` or `examples/`** (grep) — the image path in this tool is a first for the platform. Treat as unproven until the M5 spike passes on the AVD.
10. **Networking:** Ktor `3.4.2` (`gradle/libs.versions.toml:6`); `examples/weather/WeatherApi.kt:3–7` uses `ktor-client` with the **OkHttp engine** + content-negotiation — copy that stack.
11. **Tests:** `kotlin.test` only; **message is the LAST argument** (`assertEquals(expected, actual, message)`) — reverse of the May repo's JUnit4. Every ported assertion gets its arguments re-ordered.
12. **Toolchain:** JDK 17, minSdk 33, compile/target 36, Gradle 9 / AGP 8.12.3 / Kotlin 2.3.20; `:tool:clean` must be a **separate invocation** from build tasks; GitHub Packages creds are `gpr.user`/`gpr.key` in `local.properties` or `GH_PACKAGES_USER`/`GH_PACKAGES_TOKEN` env (README's names are wrong).
13. **Physical-LP3 reality (ringtone-studio precedent, verified on TLP301 2026-07-25 — `light-ringtone-studio/CLAUDE.md` M3):** unvetted sideloads run fine for UI + input (the real LP3 keyboard worked); only **token-gated server RPCs** fail pre-vetting (`NoPermission`). Light Wiki calls no `LightServiceMethod` RPC at all — nothing in this tool is gated on approval. AVD trap: fresh emulator defaults Settings → Allowed Tools to "Community Tools"; set **"All Tools"** once per AVD.

## `lighttool.toml` (committed form — byte-matched to 03 §5)

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

`id`/`label` are **Tyler-confirmed before M0 runs** (id is permanent). AVD work flips `serverPackage` locally; restore with `git checkout -- tool/lighttool.toml` before committing — CI (`reusable-submission-check.yml`) fails any PR whose committed value isn't `com.lightos`.

## Architecture

Target layout, module ownership, document model, pipeline order, network contract, storage: **03 §2–§4** (one line per file there). Two structural rules that govern every milestone:

- **Pure-JVM gate:** `parser/`, `pipeline/`, `model/`, plus `SnippetText`/`ErrorMessages`, import nothing `android.*`/`androidx.*` and are fully green under `:tool:testDebugUnitTest` **before any screen exists**.
- **Renderer seam survives:** `ArticleDocument → Compose` mapping lives in `ui/render/`; nothing above it knows about Compose.

## Milestones · checkbox tasks · definitions of done

Reviews: every milestone ends with a fresh-context code review (scope named per milestone below); one full pre-submission pass in M8.

### M0 — Scaffold, identity, CI green *(verification tier: CI)*
- [ ] Confirm with Tyler: `id`/`label` (permanent), then run the **`new-light-tool`** skill end-to-end (greenfield branch in the light-sdk clone → toml identity commit → `create-tool-repo.sh` → local clone `~/Documents/lightphone/light-wiki` → templates on a `docs/plan-of-record` branch).
- [ ] Seed repo `CLAUDE.md` from `light-workspace/ci/templates/CLAUDE.md` + this plan + 03 (carry: verified facts, toml block, architecture, milestones, defense seed, sharp edges). Seed `00-ASSESSMENT.md` (feasibility: the §Verified facts above + the two risk spikes named in M2/M5).
- [ ] **Stop for Tyler:** the two Actions secrets (`LIGHT_PACKAGES_TOKEN`, `LIGHT_CI_PAT`) — print the `gh secret set` commands for him to run; **never accept or echo a token value** (house incident rule). Resume on `gh secret list -R tyleryancey/light-wiki` showing both.
- [ ] First PR green (`check / check` + `submission-check / submission-check`).
- **DoD:** public repo, protected `main`, green first PR, plan-of-record docs merged. **Review:** scaffold conformance (toml vs this block byte-for-byte; workflows present; no stray SDK edits).

### M1 — Phase-0 verification + fixture harvest *(tier: JVM; no product code yet)*
- [ ] Re-grep every §Verified-facts item against the freshly cloned repo's vendored `plugin/`/`sdk/` (the fork pins its own copy — *that* copy is the law for this repo). Log corrections in `CLAUDE.md` if any moved.
- [ ] Harvest fixtures into `tool/src/test/resources/fixtures/`: the 3 API JSON shapes (search, pageprops, parse) + **10–15 diverse article HTML bodies** — must include: `Mercury (element)` (the May gap-bug reproducer), a heavy-infobox bio, a math-heavy page, a wide-table page, a color-coded-table page, a `Mercury` disambiguation page, a short stub. Record source URL + retrieval date + CC BY-SA attribution in `fixtures/README.md`.
- [ ] **Vet every fixture against the scan**: run the `BLOCKED_CODE_PATTERNS` regexes over the fixture files; a Wikipedia article *about Android* could legitimately contain a banned token, and the scan walks test sources with string literals **not** exempt (§facts 7). Any hit → pick a different article.
- [ ] Port `SnippetText` + `ErrorMessages` + their 16 tests (JUnit4→`kotlin.test`, messages re-ordered to last) as the porting calibration rep.
- **DoD:** fixtures committed + attributed + scan-clean; 16 ported tests green. **Review:** fixture license/attribution, scan-vet evidence, assertion-order correctness.

### M2 — Parser substrate *(tier: JVM — the risk pocket, gated)*
- [ ] `HtmlLexer`: tags/attrs/text, named + numeric entities, void elements, raw-skip for `<script>`/`<style>` content. `HtmlTree`: tolerant builder (auto-close `<p>`/`<li>`, unknown tags become transparent containers, never throw on real-world input).
- [ ] Fixture-driven: every harvested article must lex+build without error; targeted unit tests for entity edge cases, malformed nesting, comments, CDATA-ish junk.
- [ ] **Kill criterion (decided now, not in the moment):** if after ~3 focused days the substrate still fails on harvested fixtures, stop and retreat to the 03 §7.2 fallback conversation (jsoup issue / simpler source) rather than grinding — the window outranks the parser.
- **DoD:** all fixtures parse; lexer/tree suite green; zero Android imports. **Review:** fuzz-mindedness (what does malformed input do), allocation sanity on the largest fixture.

### M3 — Pipeline + document model *(tier: JVM — the May corpus lands here)*
- [ ] Port `ArticlePipeline` semantics from the May `HtmlProcessor` order (drop appendix → strip links → fix images → strip clutter → reflow infobox) with 03 §1's nine §10-resolutions applied; port `DisambigParser` semantics (namespace/red-link/interwiki rules).
- [ ] Build `ArticleDocument` extraction: headings h2–h4, paragraphs with bold/italic spans, lists (≤2 levels), blockquotes, figures (src/w/h/caption), infobox→key-value card, simple tables, math-fallback images. Unknown blocks drop silently (link-stripped reader, not an archival browser).
- [ ] Port the May parser-area assertions (28 `HtmlProcessorTest` + 12 `DisambiguationParserTest`) onto the new substrate — semantics preserved, implementation re-expressed; add model-extraction tests per block type; port the 4 `LruCache` tests with the repository.
- [ ] Update `rendering-exclusions.md` → v4 in-repo: same §§1–8 contract, enforcement points re-cited to new files, §10 replaced by 03 §1 resolutions; re-point the audit-checklist greps.
- **DoD:** ≥61 tests green (`:tool:testDebugUnitTest`), all pure-JVM; exclusions v4 committed; **pure-JVM gate closed — UI may now begin.** **Review:** exclusion-contract fidelity vs v4 doc (grep-audit both directions, the doc's own checklist method).

### M4 — Data layer + Search & chooser screens *(tier: AVD — first flip; set AVD Allowed Tools → All Tools)*
- [ ] `WikiApi` (Ktor/OkHttp engine, 3 endpoints, UA `"LightWiki/0.1 (+https://github.com/tyleryancey/light-wiki)"`), `WikiRepository` (LRUs 32/64/16, caches the parsed `ArticleDocument`, not raw HTML); JVM tests over fixture JSON; debug-build two-host allowlist assertion (03 §4).
- [ ] `ToolEntryPoint`, `SearchScreen` (`@InitialScreen`) with `LightTextField` → `LightTextInputEditor` (weather pattern), results list; `DisambiguationScreen` chooser; article stub screen (title only) to receive navigation; per-screen `LightViewModel`s; article back-stack semantics (chooser→entry→back→chooser).
- [ ] AVD walkthrough: search idle/typing/results/empty/error+Retry (airplane mode), disambig round-trip. `serverPackage` flip → work → **restore before commit**.
- **DoD:** live search-to-chooser flow on AVD; repository tests green; screens contain zero color literals, `sdk:ui` components + grid units only. **Review:** SDK-idiom audit (`grep -rn "Color(" tool/src/main` → 0; `\.dp\b` count ≈ 0 outside grid-unit helpers), blocked-import cleanliness, VM lifecycle (`onScreenShow` re-fire trap — ringtone M3 finding #1).

### M5 — Article renderer: text *(tier: AVD + JVM)*
- [ ] `BlockRenderer`: LazyColumn over blocks; `LightText` styles mapped from the May `article.css` hierarchy (lead-paragraph emphasis, heading scale, line-height); `AnnotatedString` for bold/italic; blockquote inset; list markers.
- [ ] A/A: `textScalePercent` (80–180, step 10, default 110) scales the text styles; persisted via `SealedLightContext.dataStore`; bottom-bar small-A/large-A `LightBarButton`s; **no reload, no scroll jump** on change (May acceptance, task brief Task 2).
- [ ] JVM tests for block→style mapping decisions where extractable; AVD: long-article scroll (Mercury (element)) smooth; kill+relaunch lands on Search (by design).
- **DoD:** full text-only article reads end-to-end on AVD with working A/A. **Review:** typography vs May CSS intent; main-thread hygiene (parse on `Dispatchers.Default`); monochrome audit.

### M6 — Images, infobox card, simple tables, math *(tier: AVD; opens with the platform-first spike)*
- [ ] **Spike first (½ day, gate):** hardcoded `upload.wikimedia.org` URL → OkHttp fetch → `BitmapFactory.decodeStream` → `ImageBitmap` → `Image` with saturation-0 `ColorFilter`, on the AVD. This is unprecedented in the SDK/examples (§facts 9). **If it fails for a sandbox reason: stop, record, retreat to text-only v1 (03 pre-authorizes the shape; Tyler confirms the retreat).**
- [ ] `Images.kt`: bounded in-memory bitmap LRU, aspect-ratio placeholder from model dimensions, downsample via `inSampleSize` to view width, drop-figure-on-failure (no orphan captions — 03 §1.8), grayscale filter.
- [ ] Infobox key-value card after lead paragraph (D7); simple tables (text grid, horizontal scroll when wide); math fallback images through the same image path.
- [ ] AVD pass over the fixture articles rendered live: no blank-gap regressions (the May Task-6 bug class), captions never orphaned, color-coded tables legible as text.
- **DoD:** v1 block set renders; image failure modes verified by airplane-mode mid-article. **Review:** memory behavior on image-heavy article (no unbounded bitmaps), downsampling correctness, still zero color literals (the filter, not palette hacks, does grayscale).

### M7 — Hardening + physical LP3 *(tier: AVD + physical LP3)*
- [ ] Error/offline sweep on every screen; friendly copy everywhere (no raw exception text — May Task 5 standard).
- [ ] Perf: article LRU holds parsed models; pipeline off main thread; scroll of largest fixture stays smooth; cold launch acceptable.
- [ ] Full-app grayscale/monochrome audit + `workspace-health` run (the repo now exists under `light-*` and joins the fleet sweep).
- [ ] **Physical LP3 pass** (run-light-tool skill; `serverPackage` stays committed `com.lightos` — on-device install needs no flip): sideload, drive search→chooser→article→images→A/A→back stack→offline states on hardware; keyboard input on the real LP3 keyboard (precedent says it works unvetted — §facts 13); screenshot harvest for the README (`tool/screenshots/`, ringtone M4 convention).
- **DoD:** every 03 §2 state demonstrated on hardware; screenshots on disk. **Review:** end-of-milestone full functional review against 03 §1's frozen table — anything not in the table that crept in gets cut.

### M8 — Submission package *(tier: checklist + human)*
- [ ] `SUBMISSION.md`: close every TODO. **Description paragraph: Tyler writes it** (house rule — his voice).
- [ ] `tool/README.md`: what-it-is, install, compatibility (state what was actually tested: AVD + LP3), build-from-source w/ PAT note, screenshots, license note (MIT inherited from root — resolves the May Apache-2.0 intent, D12), attribution to lightphone/light-sdk, **"Why this is a clean tool to vet" section — Tyler rewrites 03 §6 in his own words**; conformance check against `light-workspace/docs/README-CHECKLIST.md`.
- [ ] Pre-submission full review (scope below), then release: `release-tool` skill, tag `v0.1.0` (or Tyler's call on `1.0.0`), release workflow green.
- [ ] Submit per the current process (today: issue on `lightphone/light-sdk`, Fold Light #86 pattern — **Tyler files it, human-written**; re-check the process at submission time, it may have formalized).
- [ ] Optional, Tyler's call: `awesome-light` PR + Discussions→Tools post (the portfolio's known visibility gap — head-to-head finding 6).
- **DoD:** submission filed; defense doc current with the shipped behavior; backlog (03 §1) parked pending the re-vetting answer (03 §7.1).

**Pre-submission full review checks (named):** plugin-scan cleanliness incl. tests; `grep -rn webkit tool/src` → 0; `grep -rn "Color(" tool/src/main` → 0; permissions == `["android.permission.INTERNET"]` and justified in README; two-host network audit vs code; defense section vs actual behavior line-by-line (**no ledger-style false sentence** — every claim demonstrable); toml committed values (semver, `com.lightos`, monotonic versionCode); README-CHECKLIST conformance; test suite green in debug+release; `workspace-health` clean for the repo.

## Subagent plan (house subagent-driven-development — practical split)

**Delegate to subagents** (bounded, verifiable, context-heavy or mechanical):
- M1 fixture harvest + sanitize + scan-vet (report: per-fixture source/date/scan result).
- JUnit4→kotlin.test mechanical porting passes (M1/M3) — argument re-ordering is exactly the mistake-prone rote work a checker-subagent catches.
- Per-milestone code reviews — always a fresh-context subagent, never the implementing loop reviewing itself; findings logged in `CLAUDE.md` implementation notes (ringtone format).
- Audits: color-literal / grid-unit / blocked-import greps; README-CHECKLIST conformance diff.

**Keep in the main loop** (design judgment, SDK invariants, cross-file coherence): parser/tree design and its kill-criterion call; pipeline port semantics; document-model shape; renderer; screen/VM lifecycle wiring; anything that edits `lighttool.toml` or touches the toml/CI contract; all AVD/LP3 driving.

Write subagent outputs as task reports (the `.superpowers/task-reports/` habit) so reviews are auditable.

## Documentation deliverables

- Repo `CLAUDE.md` (from template at M0) is the plan of record: **updated every milestone** with implementation notes in the ringtone register — what changed from plan, what was verified and *how* (command + observed result), review findings + fixes. Source-over-doc corrections land the moment they're found.
- `00-ASSESSMENT.md` at M0; `rendering-exclusions.md` v4 at M3 (living, grep-auditable both directions).
- `tool/README.md` + screenshots at M8 — **prose and defense are explicit Tyler tasks** (scheduled above, not drafted for him).
- `SUBMISSION.md` TODO closed at M8 (description = Tyler).
- The two drafted issues (03 §7) remain drafts until Tyler rewrites and files them — re-vetting question ideally *before* M8 so the backlog release plan is grounded.

## Sharp edges

- The scan walks `tool/src/` **including test resources' neighboring `.kt` files** — and string literals are not exempt. Fixture *content* lives in `resources/` (not scanned — the walker filters `*.kt`), but any fixture snippet pasted into a `.kt` test must be vetted against `BLOCKED_CODE_PATTERNS` first (M1 does this for all fixtures anyway; keep the habit).
- `assertEquals(expected, actual, message)` — message LAST. Porting JUnit4 means re-ordering; a silently-swapped expected/actual still compiles.
- `:tool:clean` separately; never `clean assembleDebug` combined (generated manifest race).
- `serverPackage` flip is local-only; restore before every commit; CI enforces but don't lean on CI to catch it.
- AVD: Settings → Allowed Tools → "All Tools" once per fresh emulator, or RPC-less symptoms mislead you (this tool makes no RPCs, but the keyboard/editor flows still behave better verified there first).
- Every `gh` call names `-R tyleryancey/light-wiki` explicitly (the `upstream` remote can silently retarget `gh` at read-only `lightphone/light-sdk`).
- Merge PRs with `--merge`, never squash/rebase — squash breaks future upstream syncs.
- No hand-written `AndroidManifest.xml`; no `applicationId`/`versionCode`/`versionName`/`namespace` in build scripts — all from `lighttool.toml`.
- `LightTextField` cannot take typing; `LightTextInputEditor` is a full-screen destination, not an inline widget — lay out Search around that.
- SDK back stack + VMs are in-memory; process death → Search screen by design here (no restoration work).
- Wikipedia HTML shifts (the May code already handles two heading shapes — `HtmlProcessor.kt` doc comment). Fixtures pin today's shape; a live-fetch smoke test in M7 catches drift before submission.
- Don't use Claude Design for any of this, and never its "Handoff to Claude Code" export (emits React, not Compose) — standing house rule.
- Tokens/credentials never inline in commands, output, or files — hand the command to Tyler (M0 does).

## Submission checklist (instantiated from `references/vetting.md` + house CI gates)

- [ ] `permissions = ["android.permission.INTERNET"]`, one-line justification in the defense section
- [ ] Native Compose throughout — `grep -rn webkit tool/src` → 0; no WebView anywhere
- [ ] Allow-listed dependencies only — scan green is the proof; no local plugin edits
- [ ] `tool/README.md`: real prose (Tyler), screenshots (LP3/AVD captures), install, tested-on statement, build + PAT note
- [ ] "Why this is a clean tool to vet" current and line-by-line true of the shipped build
- [ ] License: MIT inherited from root `LICENSE`, stated in README; lightphone/light-sdk attribution present
- [ ] Finite-by-rule check written: every list bounded, no infinite surface, nothing designed for compulsive checking
- [ ] `lighttool.toml` committed: strict semver, monotonic `versionCode`, `serverPackage = "com.lightos"`, `orientation = "portrait"`
- [ ] Tests green (debug + release variants); pure-JVM gate intact
- [ ] Tag `v<versionName>` cut via release workflow; submission filed from the public commit (Tyler, human-written)

## Risks and mitigations

1. **Hand-rolled parser vs real-world Wikipedia HTML** (the estimate-breaker). Mitigations: fixture breadth up front (M1), tolerant tree builder, drop-unknown-gracefully, the M2 kill criterion with a pre-agreed fallback (jsoup ask / simpler source), and the seam that lets a substrate swap later.
2. **Image path unprecedented on the platform** (§facts 9). Mitigation: M6 opens with the spike; pre-authorized retreat to text-only v1 (defense actually *tightens*: one domain).
3. **Vetting latency/outcome** (Fold Light: 16 days no response; category adjacency). Mitigations: submit early in the window; defense doc done *with* the build, not after; if rejected-with-feedback, v1 is small enough to iterate; sideload distribution exists meanwhile.
4. **Window pressure** (~9–14 focused days, submissions open ~end of Aug). Mitigations: scope frozen in 03 (in/out table is the contract); M2/M6 kill criteria prevent silent overrun; Sun & Sky slate pressure is Tyler's sequencing call, not this plan's.
5. **Long-article memory/perf on LP3 hardware.** Mitigations: parsed-model LRU (not HTML strings), lazy blocks, bitmap downsampling + bounded cache, M7 hardware gate on the largest fixture.
6. **Upstream SDK moves during the build** (it shipped media3 + key-forwarding within the last week). Mitigations: weekly sync PRs are already fleet-standard; M1 re-greps pin this repo's vendored truth; `workspace-health` flags drift.
7. **Fixture licensing.** Article text is CC BY-SA — fixtures ship with attribution + source URLs (M1); they are test data in a public repo, attributed, which is the compliant shape.

## Decisions that are Tyler's

1. **Tool id + label** (`dev.tyler.wiki` / `Wiki` proposed) — permanent once published; confirm before M0.
2. **Submission `versionName`**: stay `0.1.0` or cut `1.0.0` at M8 (house guidance: `1.0.0` only if genuinely shipping-ready).
3. **README + defense prose** — scheduled M8, his voice, from 03 §6 source material.
4. **Filing the two drafted issues** (re-vetting question; jsoup allow-list) — whether, when, and in what words.
5. **Text-only retreat trigger** — if the M6 spike fails, confirm the retreat (pre-shaped in 03) rather than have the session improvise scope.
6. **Slate sequencing** — where Wiki lands relative to Sun & Sky / Ringtone Studio / Tides in the window (tides 00-ASSESSMENT recommends the trio; his call under real dates).
7. **Pre-approval visibility** — post to Discussions→Tools / awesome-light before vetting, or stay quiet until approved.
