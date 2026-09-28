package com.babycatbe.nevesestoque

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun <T> awaitSuspend(block: suspend () -> T): T {
    val completed = CountDownLatch(1)
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            outcome = result
            completed.countDown()
        }
    })
    check(completed.await(10, TimeUnit.SECONDS)) { "A coroutine do teste não terminou." }
    return outcome!!.getOrThrow()
}
