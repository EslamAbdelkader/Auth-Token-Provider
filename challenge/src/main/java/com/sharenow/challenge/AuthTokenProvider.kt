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

    private val observable : Observable<String> by lazy {
        isLoggedInObservable
            .filter { loggedIn ->
                if (!loggedIn) clearCache()
                return@filter loggedIn
            }
            .flatMap {
                Observable.concat(
                    getFromCache().filter { it.isValid(currentTime) },
                    refreshAuthToken.doOnSuccess {
                        if (!it.isValid(currentTime)) throw RuntimeException()
                        saveInCache(it)
                        computationScheduler.schedulePeriodicallyDirect({ if (token?.isValid(currentTime) == false) {
                            clearCache()
//                            observable.subscribe()
                        }}, 1L, 1L, TimeUnit.MINUTES)
                    }.retry(1).toObservable()
                ).firstElement().toObservable()
                // get from cache ==> if exists, check valid ==> if valid emit

                // else refreshToken ==> if fails retries once ==> then save in cache ==> then emit

            }
            .retry(1)
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