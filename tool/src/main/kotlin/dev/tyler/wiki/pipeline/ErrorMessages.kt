package dev.tyler.wiki.pipeline

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps exceptions surfacing through the repository → calm, user-facing copy.
 * Raw `UnknownHostException: Unable to resolve host …` strings would otherwise
 * leak into the UI (search + article error states).
 *
 * Order matters: more specific subclasses of [IOException] are checked before
 * the generic IOException branch.
 */
fun friendlyErrorMessage(e: Throwable): String = when (e) {
    is UnknownHostException -> "Can't reach Wikipedia. Check your connection."
    is SocketTimeoutException -> "Wikipedia took too long to respond. Try again."
    is IOException -> "Network problem. Check your connection and try again."
    else -> "Something went wrong. Try again."
}
