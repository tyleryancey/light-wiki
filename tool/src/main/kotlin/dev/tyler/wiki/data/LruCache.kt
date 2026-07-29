package dev.tyler.wiki.data

/**
 * Minimal thread-safe LRU cache backed by access-ordered LinkedHashMap.
 * Pure JVM so it can be exercised in unit tests.
 */
internal class LruCache<K : Any, V : Any>(private val maxSize: Int) {

    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    private val map = object : LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxSize
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun size(): Int = map.size
}
