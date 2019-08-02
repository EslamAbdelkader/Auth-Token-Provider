package com.sharenow.challenge

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.functions.BiFunction
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

    private var token: AuthToken? = null

    private val observable: Observable<String> by lazy {
        val periodicObservable: Observable<Boolean> =
            Observable.interval(0L, 1L, TimeUnit.SECONDS, computationScheduler)
                .map { isTokenValid() }
                .filter { !it }     // Emit if token is null or invalid

        periodicObservable.subscribe { clearCache() }

        // Clears cache if not logged in, and only emits if logged in
        val loggedInObservable = isLoggedInObservable.filter { loggedIn ->
            if (!loggedIn) clearCache()
            return@filter loggedIn
        }

        Observable.merge(loggedInObservable, periodicObservable)
            .flatMap {
                Observable.concat(
                    getFromCache().filter { it.isValid(currentTime) },
                    refreshAuthToken.doOnSuccess {
                        if (!it.isValid(currentTime)) throw RuntimeException()
                        saveInCache(it)
                    }.retry(1).toObservable()
                ).firstElement().toObservable()
            }
            .filter { it.isValid(currentTime) }
            .map { it.token }
            .share()
    }

    private fun isTokenValid(): Boolean = token?.isValid(currentTime) == true


    private fun getFromCache(): Observable<AuthToken> {
        return Observable.create {
            if (token == null) {
                if (!it.isDisposed) {
                    it.onComplete()
                }
            } else {
                if (!it.isDisposed) {
                    it.onNext(token!!)
                }
            }
        }
    }

    private fun saveInCache(token: AuthToken) {
        this.token = token
    }

    private fun clearCache() {
        token = null
    }

    /**
     * @return the observable auth token as a string
     */
    fun observeToken(): Observable<String> {
        return observable
    }
}