package dev.tyler.wiki.data

import dev.tyler.wiki.model.ArticleDocument
import dev.tyler.wiki.parser.HtmlTree
import dev.tyler.wiki.pipeline.ArticlePipeline
import dev.tyler.wiki.pipeline.DisambigParser
import dev.tyler.wiki.pipeline.DisambiguationSection
import dev.tyler.wiki.pipeline.stripSnippetHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row of the search-results list. */
data class SearchResult(val title: String, val snippet: String)

/**
 * Suspend facade over [WikiApi] with bounded in-memory caches (process-scoped,
 * nothing persisted). Caches hold *parsed models*, never raw HTML. Parsing
 * runs on [kotlinx.coroutines.Dispatchers.Default], off the main thread.
 */
class WikiRepository(private val api: WikiApi) {

    private val searchCache = LruCache<String, List<SearchResult>>(32)
    private val disambigCache = LruCache<String, List<DisambiguationSection>>(64)
    private val articleCache = LruCache<String, ArticleDocument>(16)

    suspend fun search(query: String): List<SearchResult> {
        val key = query.trim()
        searchCache.get(key)?.let { return it }
        val hits = api.search(key).query?.search.orEmpty()
        val results = hits.map { SearchResult(it.title, stripSnippetHtml(it.snippet)) }
        searchCache.put(key, results)
        return results
    }

    suspend fun isDisambiguation(title: String): Boolean =
        api.pageProps(title).query?.pages?.firstOrNull()?.isDisambiguation == true

    suspend fun disambigSections(title: String): List<DisambiguationSection> {
        disambigCache.get(title)?.let { return it }
        val html = api.parseArticle(title).parse?.text.orEmpty()
        val sections = withContext(Dispatchers.Default) {
            DisambigParser.parse(HtmlTree.parse(html))
        }
        disambigCache.put(title, sections)
        return sections
    }

    suspend fun article(title: String): ArticleDocument {
        articleCache.get(title)?.let { return it }
        val html = api.parseArticle(title).parse?.text.orEmpty()
        val doc = withContext(Dispatchers.Default) {
            ArticleDocument.from(ArticlePipeline.process(HtmlTree.parse(html)))
        }
        articleCache.put(title, doc)
        return doc
    }
}
