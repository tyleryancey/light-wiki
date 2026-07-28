# 00 — Feasibility & Permissibility Assessment — Wiki

This is a PER-TOOL assessment: does this specific tool clear the SDK's
technical bar and Light's approval bar. The broad cross-tool survey lives in
light-workspace, not here. Facts verified 2026-07-28 against SDK @ `d2323e3`;
M1 re-verifies against this repo's vendored copy.

## Required capabilities

- **Network (HTTPS GET)** to exactly two hosts: `en.wikipedia.org` (MediaWiki API: search, pageprops, parse) and `upload.wikimedia.org` (article images). User-initiated only.
- **Typed text input** for the search query.
- **Scrollable rich-text rendering** — headings, styled paragraphs, lists, blockquotes, key-value cards, simple tables.
- **Bitmap decode + display** for inline article images and math fallback images, grayscale.
- **One persisted preference** — `textScalePercent: Int`.
- **In-app navigation** with a back stack (Search → chooser → article, article round-trips).

## SDK surface verification

- **Network:** Ktor `3.4.2` with OkHttp engine is the demonstrated stack (`examples/weather/WeatherApi.kt:3–7`); `io.ktor` and `com.squareup.okhttp3:okhttp` are on `ALLOWED_DEPENDENCIES` (`plugin/.../LightSdkPlugin.kt`). ✅
- **Typed input:** `LightTextInputEditor` full-screen editor (`sdk/ui/.../LightTextInputEditor.kt:46,95`), driven by `rememberTextFieldState()`; `LightTextField` is the read-only opener. Canonical usage in `examples/weather`. ✅
- **Rendering:** plain Compose (`androidx.compose` allow-listed) + `sdk:ui` components; `LightThemeTokens` (`sdk/ui/.../LightTheme.kt:163`) for every color; grid units via `LightGrid` / `Float.gridUnitsAsDp()` (`LightGrid.kt:14–19`). ✅
- **Bitmap decode:** `android.graphics.BitmapFactory` is not blocked by the plugin scan, but is **used nowhere in `sdk/` or `examples/`** — an unproven path on this platform. ⚠️ Gated: M6 opens with a ½-day spike (hardcoded `upload.wikimedia.org` fetch → `decodeStream` → `ImageBitmap` with saturation-0 `ColorFilter` on the AVD). Pre-authorized retreat if it fails for a sandbox reason: text-only v1 (defense tightens to one domain).
- **Persistence:** `SealedLightContext.dataStore` (`sdk/client/.../LightActivity.kt:223–226`); `androidx.datastore` allow-listed. ✅
- **Navigation:** `LightScreen.navigateTo(...)` / `goBack(...)` (`LightScreen.kt:40,45`); per-screen `LightViewModel`s; in-memory back stack (process death → initial screen, acceptable by design — no history). ✅
- **No server RPCs:** Wiki calls no `LightServiceMethod` at all, so nothing is gated on vetting approval; unvetted sideloads run fully (ringtone-studio precedent on TLP301, 2026-07-25). ✅

## Permission allow-list check

Requesting exactly `["android.permission.INTERNET"]` — present in `ALLOWED_PERMISSIONS` (`plugin/.../LightToolMetadata.kt`). ✅ One-line justification: every byte fetched is the article the user explicitly asked for, from Wikimedia, at tap time. `ACCESS_NETWORK_STATE` deliberately absent (requested-and-unused is a documented house defect pattern). `orientation = "portrait"` validated against `ALLOWED_ORIENTATIONS = setOf("portrait")` (`LightToolMetadata.kt:145,111–113`). ✅

## Third-party dependency allow-list check

Everything used is already allow-listed: `androidx.compose`, `androidx.activity:activity-compose`, `org.jetbrains.kotlinx:kotlinx-coroutines`, `androidx.lifecycle`, `androidx.datastore`, `com.squareup.okhttp3:okhttp`, `io.ktor`, `org.jetbrains.kotlinx:kotlinx-serialization`, `org.jetbrains.kotlin:kotlin-test`. **No parsing dependency exists on the list** (no jsoup) — the HTML parser is hand-written in-repo, scoped to the `action=parse` legacy output actually observed, fixture-tested. ⚠️ Gated: M2 carries a kill criterion (~3 focused days without harvested fixtures passing → stop and fall back: jsoup allow-list issue drafted in `docs/03-v1-spec.md` §7.2, or a simpler source). No image-loading library exists on the list either — hence the hand-rolled `Images.kt` path above.

## Ethos argument

Light's approval bar: the tool "matches the Light ethos both functionally and aesthetically." Wiki is a reference lookup in the Dictionary tool's grammar — a standing community request. **Finite by construction:** one deliberately typed search → ≤20 results → one article; the screen ends where the article ends. **No engagement mechanics:** links stripped to plain text (the app cannot go where a page points), no feed, no related/trending/random, no history, no notifications, no accounts, no telemetry, no tips. **Aesthetically native:** `sdk:ui` throughout, theme tokens only, grayscale images, portrait-locked. The only setting is text size.

## Verdict

**Go**, with two explicitly gated risks, both with pre-agreed fallbacks:

1. **Hand-rolled parser vs real-world Wikipedia HTML** (M2) — kill criterion at ~3 focused days; fallback conversation pre-shaped (jsoup issue / simpler source). Fixture breadth (M1) de-risks up front.
2. **Image decode path unprecedented on the platform** (M6 opening spike) — pre-authorized retreat to text-only v1, which *tightens* the defense (one domain).

Nothing else requires a permission, dependency, or SDK surface that isn't verified above.
