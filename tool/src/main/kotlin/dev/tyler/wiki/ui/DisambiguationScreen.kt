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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
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
import dev.tyler.wiki.pipeline.DisambiguationSection

class DisambiguationViewModel(private val title: String) : LoadableViewModel<List<DisambiguationSection>>() {

    init {
        load()
    }

    override suspend fun fetch(): List<DisambiguationSection> =
        WikiGraph.repository.disambigSections(title)
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
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text(title),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
                when (val m = mode) {
                    is LoadableViewModel.Mode.Loading -> StatusLine("Loading…")

                    is LoadableViewModel.Mode.Error ->
                        ErrorRetry(m.message) { viewModel.load() }

                    is LoadableViewModel.Mode.Loaded -> {
                        // One navigation per visit. LightClickable is a bare Modifier.clickable
                        // with no debounce and Compose dispatches separate pointers to separate
                        // clickable nodes, so two fingers landing in the same frame would both
                        // reach navigateTo — which pushes unconditionally. Same guard shape as
                        // SearchViewModel.select()'s in-flight check.
                        var picked by remember { mutableStateOf(false) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            m.value.forEach { section ->
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
                                                if (!picked) {
                                                    picked = true
                                                    navigateTo({ activity -> ArticleScreen(activity, entry.title) })
                                                }
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
}
