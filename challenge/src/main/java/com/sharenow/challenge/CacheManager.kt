package com.sharenow.challenge

/**
 * An interface for caching, can be implemented by any memory / local storage caching manager
 *
 * Note: It could be argued that creating this interface conflicts with one of TDD principles,
 * that is, do the minimal effort needed to pass the tests. But I find it making sense to create
 * a single interface for caching, for the probability of changing the implementation later.
 * Maybe we will need to store the token locally (on disk), or maybe we'll implement an LRU Cache Manager
 * instead of the current implementation.
 */
interface CacheManager<K, V> {

    /**
     * Returns the value [V] assigned to the [key] if exists
     *
     * @return the value [V]
     */
    operator fun get(key: K) : V

    /**
     * Stores the [value] and assigns it to the [key]
     */
    operator fun set(key: K, value: V)

    /**
     * Removes and returns the value [V] assigned to the [key], if exists
     *
     * @return the removed value
     */
    fun remove(key: K) : V

    /**
     * Clears all data
     */
    fun clear()
}