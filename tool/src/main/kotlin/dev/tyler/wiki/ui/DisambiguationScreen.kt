package dev.tyler.wiki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.tyler.wiki.pipeline.DisambiguationSection
import dev.tyler.wiki.pipeline.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DisambiguationViewModel(private val title: String) : LightViewModel<Unit>() {

    sealed interface Mode {
        data object Loading : Mode
        data class Sections(val sections: List<DisambiguationSection>) : Mode
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
                Mode.Sections(repository.disambigSections(title))
            } catch (e: Exception) {
                Mode.Error(friendlyErrorMessage(e))
            }
        }
    }
}

/** The chooser — the app's only navigation surface. Bounded by the page's own entries. */
class DisambiguationScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
) : LightScreen<Unit, DisambiguationViewModel>(sealedActivity) {

    override val viewModelClass: Class<DisambiguationViewModel>
        get() = DisambiguationViewModel::class.java

    override fun createViewModel(): DisambiguationViewModel = DisambiguationViewModel(title)

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
                    is DisambiguationViewModel.Mode.Loading -> LightText(
                        text = "Loading…",
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                    )

                    is DisambiguationViewModel.Mode.Error -> Column {
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

                    is DisambiguationViewModel.Mode.Sections -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        m.sections.forEach { section ->
                            section.heading?.let { heading ->
                                item {
                                    LightText(
                                        text = heading,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        modifier = Modifier
                                            .padding(horizontal = 1f.gridUnitsAsDp())
                                            .padding(top = 1f.gridUnitsAsDp()),
                                    )
                                }
                            }
                            items(section.entries) { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable {
                                            navigateTo({ activity -> ArticleScreen(activity, entry.title) })
                                        }
                                        .padding(horizontal = 1f.gridUnitsAsDp())
                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                ) {
                                    LightText(text = entry.title, variant = LightTextVariant.Copy)
                                    entry.description?.let {
                                        LightText(
                                            text = it,
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
}
