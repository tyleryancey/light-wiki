# Test fixtures — English Wikipedia API harvests

The article text in these fixtures is licensed under the
[Creative Commons Attribution-ShareAlike 4.0 License (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/),
content © Wikipedia contributors. All fixtures are unmodified test data captured verbatim from the
live English Wikipedia API (`https://en.wikipedia.org/w/api.php`) on 2026-07-28: the `api/` files
are complete raw JSON responses, and the `articles/` files are the untouched HTML string extracted
from the `.parse.text` field of an `action=parse` response. The API JSON envelope itself
(`batchcomplete`, `query`, `parse` wrappers, search metadata, page props) is not article content;
the CC BY-SA licensing applies to the article text and rendered HTML bodies contributed by
Wikipedia editors.

Requests were made with `format=json&formatversion=2`, `redirects=1` where noted, and the
User-Agent `LightWiki-fixture-harvest/0.1 (+https://github.com/tyleryancey/light-wiki; tyleryancey5@gmail.com)`.

## api/ — raw API response envelopes

| Fixture | Article title | Source URL | API request URL | Retrieved |
|---|---|---|---|---|
| `api/search-mercury.json` | (search results for "mercury") | https://en.wikipedia.org/wiki/Special:Search?search=mercury | https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=mercury&srlimit=20&format=json&formatversion=2 | 2026-07-28 |
| `api/pageprops-mercury.json` | Mercury (disambiguation flag present) | https://en.wikipedia.org/wiki/Mercury | https://en.wikipedia.org/w/api.php?action=query&prop=pageprops&ppprop=disambiguation&redirects=1&titles=Mercury&format=json&formatversion=2 | 2026-07-28 |
| `api/pageprops-mercury-element.json` | Mercury (element) (no disambiguation flag) | https://en.wikipedia.org/wiki/Mercury_(element) | https://en.wikipedia.org/w/api.php?action=query&prop=pageprops&ppprop=disambiguation&redirects=1&titles=Mercury%20(element)&format=json&formatversion=2 | 2026-07-28 |
| `api/parse-stub.json` | Vestmanna (full parse envelope, short-stub article) | https://en.wikipedia.org/wiki/Vestmanna | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Vestmanna&format=json&formatversion=2 | 2026-07-28 |

## articles/ — HTML bodies (`.parse.text` extracted with `jq -r '.parse.text'`)

| Fixture | Article title | Source URL | API request URL | Retrieved |
|---|---|---|---|---|
| `articles/mercury-element.html` | Mercury (element) | https://en.wikipedia.org/wiki/Mercury_(element) | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Mercury%20(element)&format=json&formatversion=2 | 2026-07-28 |
| `articles/mercury-disambiguation.html` | Mercury (resolved from "Mercury (disambiguation)" via redirect) | https://en.wikipedia.org/wiki/Mercury | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Mercury%20(disambiguation)&format=json&formatversion=2 | 2026-07-28 |
| `articles/marie-curie.html` | Marie Curie | https://en.wikipedia.org/wiki/Marie_Curie | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Marie%20Curie&format=json&formatversion=2 | 2026-07-28 |
| `articles/fourier-transform.html` | Fourier transform | https://en.wikipedia.org/wiki/Fourier_transform | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Fourier%20transform&format=json&formatversion=2 | 2026-07-28 |
| `articles/list-countries-population-un.html` | List of countries and dependencies by population (United Nations) (resolved from "List of countries by population (United Nations)" via redirect) | https://en.wikipedia.org/wiki/List_of_countries_and_dependencies_by_population_(United_Nations) | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=List%20of%20countries%20by%20population%20(United%20Nations)&format=json&formatversion=2 | 2026-07-28 |
| `articles/list-presidents-us.html` | List of presidents of the United States | https://en.wikipedia.org/wiki/List_of_presidents_of_the_United_States | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=List%20of%20presidents%20of%20the%20United%20States&format=json&formatversion=2 | 2026-07-28 |
| `articles/vestmanna.html` | Vestmanna (short-stub article) | https://en.wikipedia.org/wiki/Vestmanna | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Vestmanna&format=json&formatversion=2 | 2026-07-28 |
| `articles/gettysburg-address.html` | Gettysburg Address (blockquote-heavy) | https://en.wikipedia.org/wiki/Gettysburg_Address | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Gettysburg%20Address&format=json&formatversion=2 | 2026-07-28 |
| `articles/great-wave-kanagawa.html` | The Great Wave off Kanagawa (image/caption-rich) | https://en.wikipedia.org/wiki/The_Great_Wave_off_Kanagawa | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=The%20Great%20Wave%20off%20Kanagawa&format=json&formatversion=2 | 2026-07-28 |
| `articles/outline-of-chemistry.html` | Outline of chemistry (nested-list-heavy) | https://en.wikipedia.org/wiki/Outline_of_chemistry | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Outline%20of%20chemistry&format=json&formatversion=2 | 2026-07-28 |
| `articles/mary-anning.html` | Mary Anning (plain mid-length biography) | https://en.wikipedia.org/wiki/Mary_Anning | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Mary%20Anning&format=json&formatversion=2 | 2026-07-28 |
| `articles/caffeine.html` | Caffeine (science article, chembox/drugbox infobox variant) | https://en.wikipedia.org/wiki/Caffeine | https://en.wikipedia.org/w/api.php?action=parse&prop=text&redirects=1&page=Caffeine&format=json&formatversion=2 | 2026-07-28 |
