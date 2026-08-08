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
        require(uri.port == -1) { "explicit port refused: $url" }
        require(uri.host in ALLOWED) { "host not on the two-host allowlist: $url" }
    }

    /** One UA for every request the tool makes — API and images alike. */
    const val USER_AGENT = "LightWiki/0.1 (+https://github.com/tyleryancey/light-wiki)"
}

/**
 * The one OkHttp client behind every request the tool makes — Ktor rides it
 * via `preconfigured`, and image fetches use it directly. One client means
 * one connection pool and one dispatcher thread pool instead of two, and its
 * interceptors enforce the request policy (allowlist + User-Agent) at the
 * seam so no call site can forget either. The policy runs both as an
 * application interceptor (fails fast, before any connection) and as a
 * network interceptor (covers every redirect hop OkHttp follows — a
 * redirect off the allowlist is refused, not silently followed). The
 * explicit assertAllowed calls at the API and image call sites remain as
 * defense-in-depth.
 */
object WikiHttp {

    private val policy = okhttp3.Interceptor { chain ->
        val request = chain.request()
        WikiHosts.assertAllowed(request.url.toString())
        chain.proceed(
            request.newBuilder().header("User-Agent", WikiHosts.USER_AGENT).build(),
        )
    }

    val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .addInterceptor(policy)
        .addNetworkInterceptor(policy)
        .build()
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
        engine { preconfigured = WikiHttp.client }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend inline fun <reified T> get(url: String): T {
        WikiHosts.assertAllowed(url)
        val response = client.get(url)
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
    }
}
