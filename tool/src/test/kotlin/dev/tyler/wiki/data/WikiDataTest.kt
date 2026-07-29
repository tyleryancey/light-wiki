package dev.tyler.wiki.data

import dev.tyler.wiki.model.Block
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** M4 data-layer tests: DTOs against real fixture JSON, host allowlist, repository caching. */
class WikiDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/api/$name")?.bufferedReader()?.readText()
            ?: fail("fixture missing: $name")

    // --- DTO decoding against captured API responses ---

    @Test
    fun `search response decodes twenty hits with titles and snippets`() {
        val response = json.decodeFromString<SearchResponse>(fixture("search-mercury.json"))
        val hits = response.query?.search ?: fail("no search results")
        assertEquals(20, hits.size)
        assertTrue(hits.any { it.title == "Mercury" })
        assertTrue(hits.all { it.title.isNotBlank() })
        assertTrue(hits.any { it.snippet.isNotBlank() })
    }

    @Test
    fun `pageprops response detects disambiguation flag both ways`() {
        val disambig = json.decodeFromString<PagepropsResponse>(fixture("pageprops-mercury.json"))
        assertTrue(disambig.query?.pages?.single()?.isDisambiguation == true, "Mercury is a disambig page")

        val article = json.decodeFromString<PagepropsResponse>(fixture("pageprops-mercury-element.json"))
        assertFalse(article.query?.pages?.single()?.isDisambiguation == true, "Mercury (element) is not")
    }

    @Test
    fun `parse response decodes title and html text`() {
        val response = json.decodeFromString<ParseResponse>(fixture("parse-stub.json"))
        val parse = response.parse ?: fail("no parse payload")
        assertEquals("Vestmanna", parse.title)
        assertTrue(parse.text.startsWith("<div"), "text is the HTML body string")
        assertTrue(parse.text.length > 10_000)
    }

    // --- Host allowlist ---

    @Test
    fun `allowlist accepts exactly the two wikimedia hosts`() {
        WikiHosts.assertAllowed("https://en.wikipedia.org/w/api.php?action=query")
        WikiHosts.assertAllowed("https://upload.wikimedia.org/wikipedia/commons/x.jpg")
        assertFailsWith<IllegalArgumentException> { WikiHosts.assertAllowed("https://example.com/x") }
        assertFailsWith<IllegalArgumentException> { WikiHosts.assertAllowed("https://en.wikipedia.org.evil.com/x") }
        assertFailsWith<IllegalArgumentException> { WikiHosts.assertAllowed("http://en.wikipedia.org/insecure") }
        assertFailsWith<IllegalArgumentException> { WikiHosts.assertAllowed("https://en.wikipedia.org@evil.com/x") }
        assertFailsWith<IllegalArgumentException> { WikiHosts.assertAllowed("https://EN.WIKIPEDIA.ORG/x") }
    }

    // --- Repository over a fake api ---

    private class FakeApi : WikiApi {
        var searchCalls = 0
        var propsCalls = 0
        var parseCalls = 0
        var parseText = "<div class=\"mw-parser-output\"><p>Hello <b>world</b>.</p></div>"
        var disambigFlag = false

        override suspend fun search(query: String): SearchResponse {
            searchCalls++
            return SearchResponse(
                SearchQuery(
                    listOf(
                        SearchHit("Mercury", "liquid <span class=\"searchmatch\">metal</span> at room"),
                    ),
                ),
            )
        }

        override suspend fun pageProps(title: String): PagepropsResponse {
            propsCalls++
            val props = if (disambigFlag) mapOf("disambiguation" to "") else null
            return PagepropsResponse(PagesQuery(listOf(PageInfo(title, props))))
        }

        override suspend fun parseArticle(title: String): ParseResponse {
            parseCalls++
            return ParseResponse(ParsePayload(title, parseText))
        }
    }

    @Test
    fun `search strips snippet html and appends truncation ellipsis`() = runBlocking {
        val repo = WikiRepository(FakeApi())
        val results = repo.search("mercury")
        assertEquals(listOf(SearchResult("Mercury", "liquid metal at room…")), results)
    }

    @Test
    fun `search results are cached per query`() = runBlocking {
        val api = FakeApi()
        val repo = WikiRepository(api)
        repo.search("mercury")
        repo.search("mercury")
        assertEquals(1, api.searchCalls, "second identical search served from cache")
        repo.search("tin")
        assertEquals(2, api.searchCalls)
    }

    @Test
    fun `isDisambiguation reflects pageprops polarity`() = runBlocking {
        val api = FakeApi()
        val repo = WikiRepository(api)
        assertFalse(repo.isDisambiguation("Mercury (element)"))
        api.disambigFlag = true
        assertTrue(repo.isDisambiguation("Mercury"))
    }

    @Test
    fun `article parses through pipeline and extraction and is cached`() = runBlocking {
        val api = FakeApi()
        val repo = WikiRepository(api)
        val doc = repo.article("Anything")
        val para = doc.blocks.single() as Block.Paragraph
        assertEquals("Hello world.", para.spans.joinToString("") { it.text })
        repo.article("Anything")
        assertEquals(1, api.parseCalls, "second read served from parsed-model cache")
    }

    // --- MediaWiki 200-with-error envelopes (M4-review finding 1) ---

    private class ErrorEnvelopeApi : WikiApi {
        var calls = 0
        override suspend fun search(query: String): SearchResponse {
            calls++
            return SearchResponse(query = null) // {"error":...} decodes to null query
        }
        override suspend fun pageProps(title: String): PagepropsResponse {
            calls++
            return PagepropsResponse(query = null)
        }
        override suspend fun parseArticle(title: String): ParseResponse {
            calls++
            return ParseResponse(parse = null)
        }
    }

    @Test
    fun `api error envelope on search throws and caches nothing`() = runBlocking {
        val api = ErrorEnvelopeApi()
        val repo = WikiRepository(api)
        assertFailsWith<java.io.IOException> { repo.search("mercury") }
        assertFailsWith<java.io.IOException> { repo.search("mercury") }
        assertEquals(2, api.calls, "an error result must never be cached")
    }

    @Test
    fun `api error envelope on article throws instead of yielding a blank document`() = runBlocking {
        val api = ErrorEnvelopeApi()
        val repo = WikiRepository(api)
        assertFailsWith<java.io.IOException> { repo.article("Gone") }
        assertFailsWith<java.io.IOException> { repo.disambigSections("Gone") }
        assertFailsWith<java.io.IOException> { repo.isDisambiguation("Gone") }
        Unit
    }

    @Test
    fun `disambig sections parse from the raw tree and cache separately`() = runBlocking {
        val api = FakeApi()
        api.parseText = """
            <div class="mw-parser-output">
            <ul><li><a href="/wiki/Mercury_(planet)" title="Mercury (planet)">Mercury (planet)</a>, a planet</li></ul>
            </div>
        """.trimIndent()
        val repo = WikiRepository(api)
        val sections = repo.disambigSections("Mercury")
        assertEquals("Mercury (planet)", sections.single().entries.single().title)
        repo.disambigSections("Mercury")
        assertEquals(1, api.parseCalls, "sections cached")
    }
}
