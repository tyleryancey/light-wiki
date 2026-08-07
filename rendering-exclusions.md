# Light Wiki — rendering exclusions inventory (v4)

What the article/disambiguation pipeline drops, strips, transforms, or leaves
out. A living doc — keep it in sync when `ArticlePipeline`, `DisambigParser`,
`ArticleDocument`, or the renderer change. Successor to the v3 inventory from
the May 2026 Phase-1 prototype (a private WebView-era donor, read-only);
v3's §10 open decisions are resolved in §10 below per `docs/03-v1-spec.md` §1.

- **§1–§4, §7–§8 are code-enforced** (auditable against the cited location).
- **§5–§6 are structurally enforced** — the WebView-era enforcement points
  they replace no longer exist by construction.
- **§9 is excluded by consequence** of the typed document model.
- **§10 records the v1 resolutions** of v3's open decisions.

Source files (all under `tool/src/main/kotlin/dev/tyler/wiki/`):
`pipeline/ArticlePipeline.kt` · `pipeline/DisambigParser.kt` ·
`model/ArticleDocument.kt`.

---

## 1. Dropped article sections
*Enforced: `ArticlePipeline.dropAppendixSections` → `APPENDIX_IDS`. Removes the
heading container (bare `<h2>` or `div.mw-heading2` wrapper) and all content to
the next h2 boundary; `mw-heading3+` subsections inside are not boundaries.*

| Heading id | Section |
|---|---|
| `See_also` | See also |
| `References` | References |
| `External_links` | External links |
| `Further_reading` | Further reading |
| `Citations` | Citations |
| `General_and_cited_sources` | General and cited sources |
| `Works_cited` | Works cited |

**Governing rule** (the last three ids landed in v1.1, joining four of
v1's original five): drop citation and external-link apparatus; **keep**
any id that can name author-written content. `Notes` and `Explanatory_notes` both
render as `ol.references` in the parsed HTML — same markup as a citation
list — so the heading id is the *only* structural signal that distinguishes
author-written explanatory prose from a reference list, and the section
*text* is the only way to confirm it (English `Notes` sections hold prose,
not citations). **Decision (Tyler, 2026-07-30): keep `Notes` and
`Explanatory_notes` out of `APPENDIX_IDS`, as v1 already shipped.** A v1.1
fix-round plan had flagged dropping `Notes` as a bug; reading the fixtures
against that plan showed both ids hold author-written explanatory prose, not
a citation list, so the ruling kept the existing behavior rather than
changing it. Each added id was confirmed against this repo's own fixture
corpus before being added, contents read rather than inferred: `Citations`
(great-wave, 92 `<li>`/43 `<cite>`; fourier short-form refs),
`General_and_cited_sources` (27/27), `Works_cited` (list-presidents, 72/72)
— all apparatus, none prose. Two ids were **removed before release** when
the pre-release review applied this rule to the wild rather than the
fixtures: `Sources`, added in v1.1 on fixture evidence (mary-anning,
23/23), is also a genuine content heading — `Nile#Sources` is the river's
headwaters section, prose and a map; and `Bibliography`, one of v1's
original five, is the works-list section on biographies — George Orwell's
`Bibliography` h2 lists the subject's own books, mid-article, before the
reference apparatus. One fixture where an id happens to be apparatus does
not make the id unambiguous; an ambiguous id stays out, because rendering
apparatus is recoverable noise and deleting content is not.

**Explicitly KEPT**: `Notes`, `Explanatory_notes`, `Sources`, and
`Bibliography`, plus every article-specific content section.
The id is matched on the `<h2>` itself or a nested `span.mw-headline` (legacy
shape), **after** stripping MediaWiki's repeated-heading `_2`/`_3`… numeric
suffix (`canonicalHeadingId` in `ArticlePipeline`, v1.1) — so a second
`References` section on the same page still drops. No id in the table above
ends in a digit, so this canonicalization cannot cause a false match within
the table itself.

---

## 2. Removed elements — article pipeline
*Enforced: `ArticlePipeline.stripClutter` → `CLUTTER_CLASSES` (+ the
`mw-gallery*` prefix rule and `sup.reference` in `isClutter`).*

| Class / rule | What it is |
|---|---|
| `mw-editsection` | "[edit]" section links |
| `navbox` | Bottom navigation template boxes |
| `vertical-navbox` | Vertical navigation boxes |
| `metadata` | Maintenance / metadata templates |
| `mw-empty-elt` | Empty placeholder nodes |
| `noprint` | Print-hidden chrome |
| `toc` | Table of contents |
| `sup.reference` | Inline citation markers ("[1]", "[2]") |
| `hatnote` | "For X, see Y." pointers *(carve-out → backlog, §10.3)* |
| `dablink` | Disambiguation pointer notes |
| `redirectMsg` | "X redirects here" messages |
| `sidebar` | "Part of a series on…" series sidebars |
| `gallery`, `mw-gallery*` | Image galleries *(new in v4 — §10.4)* |

---

## 3. Links & navigation
*Enforced: `ArticlePipeline.stripLinks` — every `<a>` unwrapped to its
children.*

Removes interactivity from **all** link types: internal article links, external
links, citation backlinks, "main article →" links, file/media links. Net
effect: no navigation anywhere in the app except the disambiguation chooser.

---

## 4. Image attributes
*Enforced: `ArticlePipeline.fixImages` / `fixImg`. Images are altered, not
excluded.*

- Removed on every `<img>`: `srcset`, `data-srcset`, `style`.
- **KEPT on every `<img>`: `width`, `height`** — v4 REVERSES v3 §4's strip
  (§10.9): the native model needs dimensions for aspect-ratio placeholders.
- `<noscript>`-wrapped `<img>` is promoted (wrapper removed); an empty
  `<noscript>` is dropped.
- `//…` src rewritten to `https://…`.

---

## 5. Style handling (supersedes v3 §5 "CSS-level exclusions")
*Structurally enforced — no CSS exists in this app.*

The parser swallows `<style>` **and `<script>`** elements whole, content
included (`HtmlLexer.RAW_SKIP_TAGS = {script, style}` — the lexer never emits
them, so §6's "no script executes" is also a lex-time guarantee), the
document model carries no style attributes forward, and the renderer draws
every color from `LightThemeTokens`. TemplateStyles, inline colors, `bgcolor`
— the entire "stray color past the dark theme" bug class of the WebView era —
cannot occur by construction (§10.1).

---

## 6. Runtime lockdown (supersedes v3 §6 "WebView lockdown")
*Structurally enforced — there is no WebView, no JavaScript, no URL handling.*

Rendering is native Compose over `ArticleDocument`; no HTML, CSS, or script
ever executes. Network egress is limited to `en.wikipedia.org` and
`upload.wikimedia.org`, asserted at both client seams on every request:
`WikiHosts.assertAllowed` in `data/WikiApi.kt` (API calls) and
`ui/render/Images.kt` (image fetches). The assertion also refuses non-https
URLs and any URL carrying an explicit port (v1.1); it runs
in all build types, not just debug.

---

## 7. Transformed (relocated, not excluded)
*Enforced: `ArticlePipeline.reflowInfobox`.*

- `table.infobox` is moved to **below the first non-blank lead paragraph**
  (direct child of `.mw-parser-output`); no-op when either is absent.
- Extraction (`ArticleDocument.extractInfobox`) then renders it as a native
  key-value card: `<caption>` → title; `tr` with both `<th>` and `<td>` →
  label/value row; rows lacking either are dropped.

---

## 8. Disambiguation-page exclusions
*Enforced: `DisambigParser` (`NON_ENTRY_CLASSES`, `NON_ARTICLE_NAMESPACES`,
`isArticleLink`). Runs on the RAW tree — disambig pages skip the article
pipeline.*

- Skipped chrome (`NON_ENTRY_CLASSES`): `sistersitebox`, `side-box`, `navbox`,
  `vertical-navbox`, `toc`, `tocright`, `metadata`, `noprint`, `thumb`,
  `mw-editsection` — both as container children and when searching links
  inside an entry.
- Chrome skipping is enforced during every text extraction too — headings and
  entry descriptions never include chrome text nested inside them (the May
  donor removed chrome globally before parsing; same net behavior).
- **Documented deviation from the donor:** entry-description trimming also
  strips a leading NBSP (U+00A0). The donor's trim set carried a duplicate
  plain space where an NBSP was evidently intended; the port trims the NBSP,
  so `<a>Foo</a>&nbsp;– gloss` yields `gloss`, not `– gloss`.
- An `<li>` is dropped **unless** its first usable `<a>` is an article link.
  A chrome-classed `<li>` is itself skipped.
  Excluded: external interwiki (`a.extiw`), red links (`a.new`), and
  non-article namespaces (`NON_ARTICLE_NAMESPACES`): `File`, `Image`, `Media`,
  `Wikipedia`, `WP`, `Help`, `Category`, `Template`, `Special`, `Portal`,
  `Talk`, `User`, `User_talk`, `Wikipedia_talk`, `Template_talk`,
  `Category_talk`, `MediaWiki`, `MediaWiki_talk`, `Module`.

---

## 9. Excluded by consequence of the typed model
*Enforced: `ArticleDocument.from` recognizes a closed block vocabulary;
everything else drops silently.*

- **Unknown block elements** (e.g. `<aside>`, `<audio>`, `<video>`, script-
  driven widgets): dropped. Transparent containers (`div`, `section`,
  `center`, `main`, `article`, `dl`, `dd`) are recursed through in place.
- **Headings h5/h6**: dropped (v1 renders h2–h4 only).
- **List nesting beyond 2 levels**: flattened into level 2.
- **Inline images inside paragraphs** (flag icons etc.): contribute nothing.
- **`sup`/`sub` styling**: text kept, superscript/subscript rendering lost
  ("mc2"); IPA/pronunciation reads as plain text (v3 recommendation stands).
- **The article H1 title**: absent from `action=parse&prop=text` by design;
  shown in the top bar.
- **Citation apparatus**: markers removed (§2) + References dropped (§1);
  `Notes` kept without its inbound markers.
- **Depth caps**: pipeline rebuilds cap at 256 levels, extraction containers
  at 64 — content nested deeper (garbage by definition) is dropped.

---

## 10. v1 resolutions of v3's open decisions (per `docs/03-v1-spec.md` §1)

1. **TemplateStyles / inline colors / `bgcolor`** → structurally moot (§5).
2. **Color-encoded data cells** → v1 renders cell *text* only
   (`extractSimpleTable`); color-to-glyph substitution → backlog.
3. **"Not to be confused" hatnote carve-out** → v1 keeps drop-all (§2);
   carve-out → backlog.
4. **Galleries** → dropped explicitly (§2) → backlog.
5. **Math** → **superseded by M6 discovery**: the planned fallback-image path
   is unrenderable in v1 — MediaWiki math fallback images are (a) served from
   `wikimedia.org/api/rest_v1/media/math/render/svg/…`, a **third host** not
   on the §6 two-host allowlist, and (b) actual **SVG**, which
   `BitmapFactory` cannot decode. Outcome: display math (`Block.MathImage`)
   is extracted but **not rendered** (`BlockRenderer` skips it); inline math
   still contributes its `alt` text (LaTeX source) to the paragraph — except
   where a paragraph's entire content is one math span, which is promoted to
   `Block.MathImage` and therefore also drops. The two-host defense claim
   stays intact. **Decision (Tyler, 2026-07-29): v1 ships with display
   equations dropped.** Rendering them is backlog item #1, post-approval —
   but *not* via the `/media/math/render/png/` variant: sampled against this
   repo's own `fourier-transform` hashes (2026-07-31), that path answers
   `200 image/png` but returns SVG bytes for **16 of 42** hashes, which
   `BitmapFactory` cannot decode either. Any backlog path has to
   rasterize the SVG itself.
6. **IPA/pronunciation** → kept as plain text (§9).
7. **Wide tables** → simplified text grid (`Block.SimpleTable`), horizontal
   scroll at render time; no grid-fidelity work in v1.
8. **Orphan captions** → superseded natively: `Block.Figure` carries
   dimensions (§4) for placeholder sizing; `Images.load`
   (`ui/render/Images.kt`) returns null on any failure, and `FigureView`
   (`ui/render/BlockRenderer.kt`) drops the whole figure — image and
   caption — on that null, and renders the caption only once the image is
   ready, so no orphan caption exists in any state, transient or settled.

---

## Audit checklist

Cross-check this doc against code from the repo root:

```sh
grep -nE "APPENDIX_IDS|CLUTTER_CLASSES|mw-gallery|reference" \
  tool/src/main/kotlin/dev/tyler/wiki/pipeline/ArticlePipeline.kt
grep -nE "NON_ENTRY_CLASSES|NON_ARTICLE_NAMESPACES|isArticleLink" \
  tool/src/main/kotlin/dev/tyler/wiki/pipeline/DisambigParser.kt
grep -nE "TRANSPARENT_CONTAINERS|MAX_DEPTH|mwe-math-element" \
  tool/src/main/kotlin/dev/tyler/wiki/model/ArticleDocument.kt
grep -n "RAW_SKIP_TAGS" \
  tool/src/main/kotlin/dev/tyler/wiki/parser/HtmlLexer.kt
```

Every selector / id / namespace / class in §1–§8 of this doc must appear in
the matching source location, and vice versa. If they don't agree, one of
them is wrong — fix the disagreement before adding any new exclusion.
