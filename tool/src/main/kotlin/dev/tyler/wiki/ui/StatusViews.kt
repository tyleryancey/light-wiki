package dev.tyler.wiki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * The status lines every screen shares: a muted single-line notice
 * (idle hint, "Loading…", "No articles found.") and the error + RETRY
 * pair. One spelling, so copy, padding, and typography cannot drift
 * per screen.
 */

@Composable
internal fun StatusLine(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        lighten = true,
        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
    )
}

@Composable
internal fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Column {
        LightText(
            text = message,
            variant = LightTextVariant.Copy,
            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
        )
        LightText(
            text = "RETRY",
            variant = LightTextVariant.Detail,
            modifier = Modifier
                .padding(horizontal = 1f.gridUnitsAsDp())
                .padding(top = 1f.gridUnitsAsDp())
                .lightClickable { onRetry() },
        )
    }
}
