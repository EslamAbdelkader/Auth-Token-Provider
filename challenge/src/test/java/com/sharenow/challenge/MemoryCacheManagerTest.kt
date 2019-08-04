package com.sharenow.challenge

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryCacheManager]
 */
class MemoryCacheManagerTest {

    /**
     * The [MemoryCacheManager] under test
     */
    private lateinit var cacheManager: CacheManager<String, Any?>

    /**
     * Reinitializing cache manager before every test, to make sure no data is stored.
     *
     * Note: Didn't want to use [CacheManager.clear] because it would depend on the implementation.
     */
    @Before
    fun setup() {
        cacheManager = MemoryCacheManager()
    }

    /**
     * Basic functionality, storing and retrieving the value using the same string key,
     * not necessarily the same string object
     */
    @Test
    fun `A value can be retrieved after storing using the same key`() {
        // Given
        val originalValue = Object()
        cacheManager["key"] = originalValue

        // When
        val retrievedValue = cacheManager["key"]

        // Then
        assertThat(retrievedValue).isEqualTo(originalValue)
    }

    /**
     * The cache manager returns null (not any default value) if the key is never stored
     */
    @Test
    fun `A key that is not stored returns null value`() {
        // Given the key "key" is never stored

        // When
        val retrievedValue = cacheManager["key"]

        // Then
        assertThat(retrievedValue).isNull()
    }

    /**
     * The cache manager returns null (not any default value) if the key is removed
     */
    @Test
    fun `A key that has been removed returns null value`() {
        // Given
        cacheManager["key"] = Object()
        cacheManager.remove("key")

        // When
        val retrievedValue = cacheManager["key"]

        // Then
        assertThat(retrievedValue).isNull()
    }

    /**
     * The cache manager returns null (not any default value) for any key after clearing
     */
    @Test
    fun `A key returns null whenever the cache manager is cleared`(){
        // Given
        cacheManager["key"] = Object()
        cacheManager.clear()

        // When
        val retrievedValue = cacheManager["key"]

        // Then
        assertThat(retrievedValue).isNull()
    }
}