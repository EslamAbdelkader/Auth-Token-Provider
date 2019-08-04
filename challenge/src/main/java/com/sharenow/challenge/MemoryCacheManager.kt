package com.sharenow.challenge

/**
 * Memory Cache Manager that saves, restores, and removes any (nullable) item with a string key.
 */
class MemoryCacheManager : CacheManager<String, Any?> {

    /**
     * Actual storage
     */
    private val storage = mutableMapOf<String, Any?>()

    override operator fun get(key: String) = storage[key]

    override operator fun set(key: String, value: Any?) {
        storage[key] = value
    }

    override fun remove(key: String) = storage.remove(key)

    override fun clear() = storage.clear()
}