package dev.tyler.wiki.ui

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import dev.tyler.wiki.pipeline.friendlyErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The load-once state machine the Disambiguation and Article screens share:
 * Loading → Loaded(value) | Error(message), with RETRY re-running [load].
 * Subclasses provide [fetch] and call [load] from their own init block, so
 * it runs after their fields exist.
 */
abstract class LoadableViewModel<T> : LightViewModel<Unit>() {

    sealed interface Mode<out T> {
        data object Loading : Mode<Nothing>
        data class Loaded<T>(val value: T) : Mode<T>
        data class Error(val message: String) : Mode<Nothing>
    }

    val mode = MutableStateFlow<Mode<T>>(Mode.Loading)

    protected abstract suspend fun fetch(): T

    fun load() {
        mode.value = Mode.Loading
        viewModelScope.launch {
            mode.value = try {
                Mode.Loaded(fetch())
            } catch (e: Exception) {
                Mode.Error(friendlyErrorMessage(e))
            }
        }
    }
}
