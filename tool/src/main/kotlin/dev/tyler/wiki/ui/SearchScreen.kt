package dev.tyler.wiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.wiki.data.SearchResult
import dev.tyler.wiki.pipeline.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : LightViewModel<Unit>() {

    sealed interface Mode {
        data object Idle : Mode
        data object Input : Mode
        data object Loading : Mode
        data class Results(val results: List<SearchResult>) : Mode
        data object Empty : Mode
        data class Error(val message: String) : Mode
    }

    sealed interface NavTarget {
        data class Chooser(val title: String) : NavTarget
        data class Article(val title: String) : NavTarget
    }

    val mode = MutableStateFlow<Mode>(Mode.Idle)
    val query = MutableStateFlow("")
    val inputSession = MutableStateFlow(0)
    val navTarget = MutableStateFlow<NavTarget?>(null)

    private val repository = WikiGraph.repository

    fun openInput() {
        inputSession.value++
        mode.value = Mode.Input
    }

    fun cancelInput() {
        mode.value = if (query.value.isBlank()) Mode.Idle else modeAfterSearch()
    }

    fun submit(text: CharSequence) {
        val q = text.toString().trim()
        if (q.isEmpty()) {
            mode.value = Mode.Idle
            return
        }
        query.value = q
        runSearch()
    }

    fun retry() {
        if (query.value.isNotBlank()) runSearch() else mode.value = Mode.Idle
    }

    private var lastResults: List<SearchResult> = emptyList()

    private fun modeAfterSearch(): Mode =
        if (lastResults.isEmpty()) Mode.Empty else Mode.Results(lastResults)

    private fun runSearch() {
        mode.value = Mode.Loading
        viewModelScope.launch {
            mode.value = try {
                val results = repository.search(query.value)
                lastResults = results
                if (results.isEmpty()) Mode.Empty else Mode.Results(results)
            } catch (e: Exception) {
                Mode.Error(friendlyErrorMessage(e))
            }
        }
    }

    fun select(title: String) {
        mode.value = Mode.Loading
        viewModelScope.launch {
            try {
                val disambig = repository.isDisambiguation(title)
                navTarget.value = if (disambig) NavTarget.Chooser(title) else NavTarget.Article(title)
            } catch (e: Exception) {
                mode.value = Mode.Error(friendlyErrorMessage(e))
            }
        }
    }

    fun navConsumed() {
        navTarget.value = null
        mode.value = modeAfterSearch()
    }
}

@InitialScreen
class SearchScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SearchViewModel>(sealedActivity) {

    override val viewModelClass: Class<SearchViewModel> get() = SearchViewModel::class.java

    override fun createViewModel(): SearchViewModel = SearchViewModel()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val mode by viewModel.mode.collectAsState()
        val query by viewModel.query.collectAsState()
        val inputSession by viewModel.inputSession.collectAsState()
        val navTarget by viewModel.navTarget.collectAsState()
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LaunchedEffect(navTarget) {
            when (val target = navTarget) {
                is SearchViewModel.NavTarget.Chooser -> {
                    viewModel.navConsumed()
                    navigateTo({ activity -> DisambiguationScreen(activity, target.title) })
                }
                is SearchViewModel.NavTarget.Article -> {
                    viewModel.navConsumed()
                    navigateTo({ activity -> ArticleScreen(activity, target.title) })
                }
                null -> Unit
            }
        }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val m = mode) {
                    is SearchViewModel.Mode.Input -> LightTextInputEditor(
                        title = "Search",
                        editorKey = inputSession,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        state = textFieldState,
                        onSubmit = viewModel::submit,
                        onBack = viewModel::cancelInput,
                        submitIcon = LightIcons.SEARCH,
                        singleLine = true,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is SearchViewModel.Mode.Idle -> SearchFrame(query) {
                        LightText(
                            text = "Look something up on Wikipedia.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                    }

                    is SearchViewModel.Mode.Loading -> SearchFrame(query) {
                        LightText(
                            text = "Searching…",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                    }

                    is SearchViewModel.Mode.Empty -> SearchFrame(query) {
                        LightText(
                            text = "No articles found.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                    }

                    is SearchViewModel.Mode.Error -> SearchFrame(query) {
                        LightText(
                            text = m.message,
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "RETRY",
                            variant = LightTextVariant.Detail,
                            modifier = Modifier
                                .padding(horizontal = 1f.gridUnitsAsDp())
                                .padding(top = 1f.gridUnitsAsDp())
                                .lightClickable { viewModel.retry() },
                        )
                    }

                    is SearchViewModel.Mode.Results -> SearchFrame(query) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(m.results) { result ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable { viewModel.select(result.title) }
                                        .padding(horizontal = 1f.gridUnitsAsDp())
                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                ) {
                                    LightText(text = result.title, variant = LightTextVariant.Copy)
                                    if (result.snippet.isNotBlank()) {
                                        LightText(
                                            text = result.snippet,
                                            variant = LightTextVariant.Detail,
                                            lighten = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchFrame(query: String, body: @Composable () -> Unit) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                center = LightTopBarCenter.Text("Wiki"),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            LightTextField(
                label = "Search",
                value = query,
                placeholder = "Search Wikipedia",
                onClick = viewModel::openInput,
                modifier = Modifier
                    .padding(horizontal = 1f.gridUnitsAsDp())
                    .padding(bottom = 1f.gridUnitsAsDp()),
            )
            body()
        }
    }
}
