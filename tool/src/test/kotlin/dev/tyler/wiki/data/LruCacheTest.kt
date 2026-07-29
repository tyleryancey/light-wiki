package dev.tyler.wiki.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LruCacheTest {

    @Test
    fun `stores and retrieves values`() {
        val cache = LruCache<String, Int>(maxSize = 3)
        cache.put("a", 1)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("missing"))
    }

    @Test
    fun `evicts least recently used once full`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3) // evicts "a"
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `getting a value marks it recent`() {
        val cache = LruCache<String, Int>(maxSize = 2)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.get("a") // mark a as most recent
        cache.put("c", 3) // evicts "b", not "a"
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals(3, cache.get("c"))
    }

    @Test
    fun `reports size`() {
        val cache = LruCache<String, Int>(maxSize = 5)
        assertEquals(0, cache.size())
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(2, cache.size())
    }
}
