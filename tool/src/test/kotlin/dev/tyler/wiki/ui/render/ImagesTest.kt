package dev.tyler.wiki.ui.render

import kotlin.test.Test
import kotlin.test.assertEquals

class ImagesTest {

    @Test
    fun `sample size yields decoded width at or under the cap`() {
        // M6-review finding 1: the loop must guarantee width/sample <= max.
        assertEquals(1, Images.sampleSize(1080, 1080))
        assertEquals(1, Images.sampleSize(250, 1080))
        assertEquals(2, Images.sampleSize(1081, 1080))
        assertEquals(2, Images.sampleSize(2159, 1080), "2159/1=2159 exceeds cap; /2=1079 fits")
        assertEquals(2, Images.sampleSize(2160, 1080))
        assertEquals(4, Images.sampleSize(4320, 1080))
        assertEquals(2, Images.sampleSize(2161, 1080), "integer division: 2161/2 = 1080, within cap")
    }
}
