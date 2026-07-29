package dev.tyler.wiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.wiki.model.ArticleDocument
import dev.tyler.wiki.pipeline.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ArticleViewModel(private val title: String) : LightViewModel<Unit>() {

    sealed interface Mode {
        data object Loading : Mode
        data class Loaded(val document: ArticleDocument) : Mode
        data class Error(val message: String) : Mode
    }

    val mode = MutableStateFlow<Mode>(Mode.Loading)

    private val repository = WikiGraph.repository

    init {
        load()
    }

    fun load() {
        mode.value = Mode.Loading
        viewModelScope.launch {
            mode.value = try {
                Mode.Loaded(repository.article(title))
            } catch (e: Exception) {
                Mode.Error(friendlyErrorMessage(e))
            }
        }
    }
}

/**
 * M4 stub: proves navigation + the data path (block count). The real
 * BlockRenderer body lands in M5.
 */
class ArticleScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
) : LightScreen<Unit, ArticleViewModel>(sealedActivity) {

    override val viewModelClass: Class<ArticleViewModel> get() = ArticleViewModel::class.java

    override fun createViewModel(): ArticleViewModel = ArticleViewModel(title)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val mode by viewModel.mode.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text(title),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
                when (val m = mode) {
                    is ArticleViewModel.Mode.Loading -> LightText(
                        text = "Loading…",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                    )

                    is ArticleViewModel.Mode.Error -> Column {
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
                                .lightClickable { viewModel.load() },
                        )
                    }

                    is ArticleViewModel.Mode.Loaded -> LightText(
                        text = "Article loaded: ${m.document.blocks.size} blocks. Renderer lands in M5.",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}
