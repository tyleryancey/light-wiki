package dev.tyler.wiki.ui

import dev.tyler.wiki.data.KtorWikiApi
import dev.tyler.wiki.data.WikiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-scoped service graph — one client, one repository, shared by all screens. */
object WikiGraph {
    val repository: WikiRepository by lazy { WikiRepository(KtorWikiApi()) }

    /**
     * Process-lifetime scope for writes that must outlive a screen's
     * ViewModel (e.g. persisting the A/A pref while the user backs out —
     * viewModelScope is cancelled on pop, dropping the edit).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
