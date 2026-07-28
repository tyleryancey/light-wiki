package dev.tyler.wiki.pipeline

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMessagesTest {

    @Test
    fun unknownHostMapsToConnectionCopy() {
        assertEquals(
            "Can't reach Wikipedia. Check your connection.",
            friendlyErrorMessage(UnknownHostException("en.wikipedia.org")),
        )
    }

    @Test
    fun timeoutMapsToTimeoutCopy() {
        assertEquals(
            "Wikipedia took too long to respond. Try again.",
            friendlyErrorMessage(SocketTimeoutException("read timeout")),
        )
    }

    @Test
    fun otherIoMapsToGenericNetworkCopy() {
        assertEquals(
            "Network problem. Check your connection and try again.",
            friendlyErrorMessage(IOException("stream closed")),
        )
    }

    @Test
    fun nonIoFallsThroughToGeneric() {
        assertEquals(
            "Something went wrong. Try again.",
            friendlyErrorMessage(IllegalStateException("bad state")),
        )
    }
}
