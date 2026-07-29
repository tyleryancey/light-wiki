package dev.tyler.wiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
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
import dev.tyler.wiki.ui.render.ArticleTypography
import dev.tyler.wiki.ui.render.BlockRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ArticleViewModel(
    private val title: String,
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    sealed interface Mode {
        data object Loading : Mode
        data class Loaded(val document: ArticleDocument) : Mode
        data class Error(val message: String) : Mode
    }

    val mode = MutableStateFlow<Mode>(Mode.Loading)
    val scalePercent = MutableStateFlow(ArticleTypography.SCALE_DEFAULT)

    private val repository = WikiGraph.repository

    init {
        load()
        viewModelScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) } // unreadable prefs → default scale, never a crash
                .map { it[SCALE_KEY] ?: ArticleTypography.SCALE_DEFAULT }
                .collect { scalePercent.value = it }
        }
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

    fun increaseScale() = persistScale(ArticleTypography.stepUp(scalePercent.value))

    fun decreaseScale() = persistScale(ArticleTypography.stepDown(scalePercent.value))

    private fun persistScale(value: Int) {
        scalePercent.value = value // immediate: no reload, no wait for disk
        // App scope, not viewModelScope: backing out of the screen must not
        // cancel the write (M5 review finding 4b).
        WikiGraph.appScope.launch {
            try {
                dataStore.edit { it[SCALE_KEY] = value }
            } catch (_: Exception) {
                // A failed persist must never crash reading; scale stays in memory.
            }
        }
    }

    private companion object {
        val SCALE_KEY = intPreferencesKey("textScalePercent")
    }
}

/** One finite article; the screen ends where the article ends. */
class ArticleScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
) : LightScreen<Unit, ArticleViewModel>(sealedActivity) {

    override val viewModelClass: Class<ArticleViewModel> get() = ArticleViewModel::class.java

    override fun createViewModel(): ArticleViewModel =
        ArticleViewModel(title, lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val mode by viewModel.mode.collectAsState()
        val scale by viewModel.scalePercent.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text(title),
                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
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

                    is ArticleViewModel.Mode.Loaded -> {
                        BlockRenderer(
                            blocks = m.document.blocks,
                            scalePercent = scale,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        LightBottomBar(
                            items = listOf(
                                LightBarButton.Text(
                                    text = "a",
                                    contentDescription = "Smaller text",
                                    onClick = viewModel::decreaseScale,
                                ),
                                LightBarButton.Text(
                                    text = "A",
                                    contentDescription = "Larger text",
                                    onClick = viewModel::increaseScale,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}
