package com.buddylabs.pinglide.ui.components

import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestMessageActionTest {
    @Test
    fun successExpiresAtTheRecommendedTimeoutAfterDelivery() =
        assertNoticeExpiresAfterDelivery(TestMessageResult.Received)

    @Test
    fun offlineFeedbackExpiresAtTheRecommendedTimeoutAfterDelivery() =
        assertNoticeExpiresAfterDelivery(TestMessageResult.Offline)

    private fun assertNoticeExpiresAfterDelivery(result: TestMessageResult) = runTest {
        val delivery = CompletableDeferred<TestMessageOutcome>()
        val outcome = TestMessageOutcome("Laptop", result)
        val action = TestMessageAction(this) { delivery.await() }
        action.enter("laptop")
        action.send(5_000L)
        advanceTimeBy(6_000L)
        assertTrue(action.state.value.pending)

        delivery.complete(outcome)
        runCurrent()
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(outcome, action.state.value.outcome)

        advanceTimeBy(1L)
        runCurrent()
        assertNull(action.state.value.outcome)
    }
}
