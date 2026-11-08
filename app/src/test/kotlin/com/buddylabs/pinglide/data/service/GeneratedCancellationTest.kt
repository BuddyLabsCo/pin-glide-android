package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.generated.http.api.SystemApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedCancellationTest {
    @Test
    fun cancellingGeneratedCoroutineCancelsUnderlyingCall() = runBlocking {
        withTimeout(5_000) {
            val enqueued = CompletableDeferred<Unit>()
            val pending = PendingCall(enqueued)
            val api = SystemApi(client = Call.Factory { pending })
            val request = launch { api.getHealth() }
            enqueued.await()
            request.cancelAndJoin()
            assertTrue(pending.isCanceled())
        }
    }

    private class PendingCall(private val enqueued: CompletableDeferred<Unit>) :
        Call by OkHttpClient().newCall(
            Request.Builder().url("https://example.invalid/health").build()
        ) {
        @Volatile
        private var cancelled = false

        override fun execute(): Response = error("Generated coroutine must enqueue asynchronously.")

        override fun enqueue(responseCallback: Callback) {
            enqueued.complete(Unit)
        }

        override fun cancel() {
            cancelled = true
        }

        override fun isExecuted(): Boolean = enqueued.isCompleted

        override fun isCanceled(): Boolean = cancelled

        public override fun clone(): Call = error("This probe must not retry or clone a call.")
    }
}
