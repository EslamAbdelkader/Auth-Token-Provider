package com.sharenow.challenge

import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import org.threeten.bp.ZonedDateTime
import java.util.concurrent.TimeUnit

class AuthTokenProvider(

    /**
     * Scheduler used for background operations. Execute time relevant operations on this one,
     * so we can use [TestScheduler] within unit tests.
     */
    private val computationScheduler: Scheduler,

    /**
     * Single to be observed in order to get a new token.
     */
    private val refreshAuthToken: Single<AuthToken>,

    /**
     * Observable for the login state of the user. Will emit true, if he is logged in.
     */
    private val isLoggedInObservable: Observable<Boolean>,

    /**
     * Function that returns you the current time, whenever you need it. Please use this whenever you check the
     * current time, so we can manipulate time in unit tests.
     */
    private val currentTime: () -> ZonedDateTime
) {

    /**
     * The cache manager that will handle caching, retrieving, and removing of the [AuthToken]
     *
     * Note: In my opinion, it needs to be injected into the Provider, maybe using a dependency
     * injection framework like Dagger. But I can't do that without changing the test class
     */
    private val cacheManager: CacheManager<String, Any?> = MemoryCacheManager()

    /**
     * The token observable on which to be subscribed by whoever is interested in a token.
     * It abstracts all the process of token retrieval, caching, and validity checking.
     */
    private val tokenObservable: Observable<String> by lazy {
        createTokenObservable()
    }

    /**
     * An observable that constantly checks cached token for
     */
    private val cacheChecker: Observable<Boolean> by lazy {
        createTokenValidityCheckerObservable().also {
            // keep the checking running forever, and remove cache whenever invalid
            it.subscribe { removeCachedToken() }
        }
    }

    /**
     * Creates the token observable, and abstracts the whole process of token retrieval,
     * caching, and validity checking.
     *
     * It subscribes on any event coming from either :
     * - [filteredLoggedInObservable] (meaning user logged in)
     * - or [cacheChecker] (meaning token has become invalid)
     *
     * whenever an event is emitted, it tries to fetch a token from two observables (in order):
     * - [fromCacheObservable] which emits a valid token or completes silently
     * - or [fromNetworkObservable] which emits a valid token or error if failed
     *
     * If a token is retrieved, then it must be valid, we map it to a string and emit it.
     *
     * Finally, the observable is shared between all con-current subscribers
     *
     * @return the token observable
     */
    private fun createTokenObservable(): Observable<String> {
        val filteredLoggedIn = filteredLoggedInObservable()

        return Observable.merge(filteredLoggedIn, cacheChecker)
            .flatMap {
                val fromCache = fromCacheObservable()
                val fromNetwork = fromNetworkObservable()

                Observable.concat(fromCache, fromNetwork)
                    .firstElement()
                    .toObservable()
            }
            .map { it.token }
            .share()
    }

    /**
     * Returns the tokenObservable, which is only created once,
     * in order not to call [Observable.share] multiple times.
     *
     * @return the observable auth token as a string
     */
    fun observeToken(): Observable<String> {
        return tokenObservable
    }

    /**
     * Create the periodic cache-validity-checker observable
     * It runs every 1 minute, gets token from cache, checks if token is null,
     * or token is invalid, and emit an event in any of these cases.
     * As long as the token is not null & is valid, it doesn't emit anything
     *
     * @return the periodic cache checker observable
     */
    private fun createTokenValidityCheckerObservable(): Observable<Boolean> {
        return Observable.interval(
            INITIAL_DELAY_ZERO,
            INTERVAL_1_MINUTE,
            TimeUnit.MINUTES,
            computationScheduler
        ).map {
            val token = getCachedToken()
            isTokenValid(token)
        }.filter { !it }                 // Emit if token is null or invalid
    }

    /**
     * Creates the cache retrieval observable.
     * The cache retrieval observable runs exactly once, hence [Maybe.fromCallable]
     * It gets the cached token, and checks for its validity
     * It emits the value only if not null & is valid
     *
     * @return the cache retrieval observable
     */
    private fun fromCacheObservable(): Observable<AuthToken> {
        return Maybe.fromCallable<AuthToken> { getCachedToken() }
            .filter { isTokenValid(it) }
            .toObservable()
    }

    /**
     * Creates the network retrieval observable.
     * It gets the token from [refreshAuthToken], and checks for its validity,
     * if the token is valid, it's saved in cache and emitted,
     * In case of [refreshAuthToken]'s error, or invalid token, it retries exactly once
     *
     * @return the network retrieval observable
     */
    private fun fromNetworkObservable(): Observable<AuthToken>? {
        return refreshAuthToken.doOnSuccess {
            if (isTokenValid(it)) {
                saveInCache(it)
            } else {
                // Throws an error to be caught by [retry]
                throw RuntimeException("Invalid token from network")
            }
        }.retry(1).toObservable()
    }

    /**
     * Adds a filter operator on [isLoggedInObservable]
     * This filter handles when user logs out by removing cached token, and only
     * emits events where user is logged in
     *
     * @return the filtered logged in observable
     */
    private fun filteredLoggedInObservable(): Observable<Boolean>? {
        return isLoggedInObservable
            .doOnNext { loggedIn -> if (!loggedIn) removeCachedToken() }
            .filter { it }
    }

    /**
     * @param token The token to be checked for validity
     * @return true if token is not null & is valid, false otherwise
     */
    private fun isTokenValid(token: AuthToken?): Boolean {
        return token?.isValid(currentTime) == true
    }

    /**
     * Retrieves token from cache
     *
     * @return token if not null & is instance of [AuthToken], null otherwise
     */
    private fun getCachedToken() = cacheManager[TOKEN] as? AuthToken

    /**
     * Saves token in cache
     *
     * @param token The token to be saved
     */
    private fun saveInCache(token: AuthToken) {
        cacheManager[TOKEN] = token
    }

    /**
     * Removes token from cache
     */
    private fun removeCachedToken() {
        cacheManager.remove(TOKEN)
    }

    companion object {

        /**
         * Key used for storing and retrieving token from cache
         */
        private const val TOKEN = "token"

        /**
         * The interval at which the periodic observable will check the cache for token validity
         */
        private const val INTERVAL_1_MINUTE = 1L

        /**
         * The initial delay before the periodic observable will check the cache for token validity
         */
        private const val INITIAL_DELAY_ZERO = 0L
    }
}