package dev.tyler.wiki.ui

import dev.tyler.wiki.data.KtorWikiApi
import dev.tyler.wiki.data.WikiRepository

/** Process-scoped service graph — one client, one repository, shared by all screens. */
object WikiGraph {
    val repository: WikiRepository by lazy { WikiRepository(KtorWikiApi()) }
}
