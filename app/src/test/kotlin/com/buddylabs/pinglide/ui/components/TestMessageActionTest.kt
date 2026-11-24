package com.buddylabs.pinglide.ui.components

import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestMessageActionTest {
    @Test
    fun successExpiresAtTheRecommendedTimeoutAfterDelivery() =
        assertNoticeLifetime(TestMessageOutcome("Laptop", TestMessageResult.Received))

    @Test
    fun offlineFeedbackExpiresAtTheRecommendedTimeoutAfterDelivery() =
        assertNoticeLifetime(TestMessageOutcome("Laptop", TestMessageResult.Offline))

    @Test
    fun acknowledgmentTimeoutFeedbackExpiresAtTheRecommendedTimeoutAfterDelivery() =
        assertNoticeLifetime(TestMessageOutcome("Laptop", TestMessageResult.AckTimeout))

    @Test
    fun uncertainFeedbackExpiresAtTheRecommendedTimeoutAfterDelivery() =
        assertNoticeLifetime(TestMessageOutcome("Laptop", TestMessageResult.Uncertain))

    @Test
    fun confirmedRemovalExpiresAtTheRecommendedTimeoutAfterDelivery() = assertNoticeLifetime(
        TestMessageOutcome("Laptop", TestMessageResult.InvalidPairing, pairingRemoved = true)
    )

    private fun assertNoticeLifetime(outcome: TestMessageOutcome) = runTest {
        val delivery = CompletableDeferred<TestMessageOutcome>()
        val action = TestMessageAction(this) { delivery.await() }
        action.enter("laptop")
        action.send(5_000L)
        advanceTimeBy(6_000L)
        assertTrue(action.state.value.pending)

        delivery.complete(outcome)
        runCurrent()
        assertFalse(action.state.value.announceReceived)
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(outcome, action.state.value.outcome)

        advanceTimeBy(1L)
        runCurrent()
        assertNull(action.state.value.outcome)
    }
}
