package dev.tyler.wiki.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URLEncoder

// --- DTOs: MediaWiki API, formatversion=2 ---

@Serializable
data class SearchResponse(val query: SearchQuery? = null)

@Serializable
data class SearchQuery(val search: List<SearchHit> = emptyList())

@Serializable
data class SearchHit(val title: String, val snippet: String = "")

@Serializable
data class PagepropsResponse(val query: PagesQuery? = null)

@Serializable
data class PagesQuery(val pages: List<PageInfo> = emptyList())

@Serializable
data class PageInfo(
    val title: String = "",
    val pageprops: Map<String, String>? = null,
) {
    /** formatversion=2: the `disambiguation` key is present (empty string) on disambig pages. */
    val isDisambiguation: Boolean get() = pageprops?.containsKey("disambiguation") == true
}

@Serializable
data class ParseResponse(val parse: ParsePayload? = null)

@Serializable
data class ParsePayload(val title: String = "", val text: String = "")

// --- Host allowlist ---

/**
 * The only two hosts this tool ever contacts. Asserted at the client seam on
 * every request — makes the vetting-defense claim mechanical.
 */
object WikiHosts {
    val ALLOWED = setOf("en.wikipedia.org", "upload.wikimedia.org")

    fun assertAllowed(url: String) {
        val uri = java.net.URI(url)
        require(uri.scheme == "https") { "non-https request refused: $url" }
        require(uri.host in ALLOWED) { "host not on the two-host allowlist: $url" }
    }
}

// --- API surface ---

interface WikiApi {
    suspend fun search(query: String): SearchResponse
    suspend fun pageProps(title: String): PagepropsResponse
    suspend fun parseArticle(title: String): ParseResponse
}

/**
 * Live MediaWiki client — Ktor with the OkHttp engine (the `examples/weather`
 * stack). GET only, `formatversion=2`, descriptive User-Agent per Wikimedia
 * etiquette, and the two-host allowlist asserted on every request.
 */
class KtorWikiApi : WikiApi {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend inline fun <reified T> get(url: String): T {
        WikiHosts.assertAllowed(url)
        val response = client.get(url) {
            headers.append("User-Agent", USER_AGENT)
        }
        if (!response.status.isSuccess()) {
            throw IOException("Wikipedia HTTP ${response.status.value}")
        }
        return response.body()
    }

    override suspend fun search(query: String): SearchResponse =
        get("$BASE?action=query&list=search&srsearch=${enc(query)}&srlimit=20&format=json&formatversion=2")

    override suspend fun pageProps(title: String): PagepropsResponse =
        get("$BASE?action=query&prop=pageprops&ppprop=disambiguation&redirects=1&titles=${enc(title)}&format=json&formatversion=2")

    override suspend fun parseArticle(title: String): ParseResponse =
        get("$BASE?action=parse&prop=text&redirects=1&page=${enc(title)}&format=json&formatversion=2")

    private fun enc(s: String): String = URLEncoder.encode(s.trim(), Charsets.UTF_8.name())

    private companion object {
        const val BASE = "https://en.wikipedia.org/w/api.php"
        const val USER_AGENT = "LightWiki/0.1 (+https://github.com/tyleryancey/light-wiki)"
    }
}
