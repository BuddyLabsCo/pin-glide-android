package com.buddylabs.pinglide.ui.components

import androidx.annotation.StringRes
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TestMessageAction(
    private val scope: CoroutineScope,
    private val sendTest: suspend (String) -> TestMessageOutcome?
) {
    private val mutableState = MutableStateFlow(TestMessageState())
    val state = mutableState.asStateFlow()
    var pairingId: String? = null
        private set
    private var epoch = 0L
    private var operation: Job? = null

    fun enter(target: String?) {
        if (pairingId == target) return
        leave()
        pairingId = target
    }

    fun leave() {
        epoch++
        operation?.cancel()
        operation = null
        pairingId = null
        mutableState.value = TestMessageState()
    }

    fun send(noticeMillis: Long?) {
        val target = pairingId ?: return
        if (state.value.pending || state.value.announceReceived) return
        operation?.cancel()
        val expected = epoch
        mutableState.value = TestMessageState(pending = true)
        operation = scope.launch {
            val result = sendTest(target)
            if (expected == epoch) {
                val received = result?.result == TestMessageResult.Received
                mutableState.value = TestMessageState(
                    outcome = result,
                    announceReceived = received && noticeMillis == null
                )
                if (result?.detailsNoticeLabel != null && noticeMillis != null) {
                    delay(noticeMillis)
                    mutableState.value = TestMessageState()
                }
            }
        }
    }

    fun consumeSuccess(): Boolean {
        if (!state.value.announceReceived) return false
        mutableState.value = state.value.copy(announceReceived = false)
        return true
    }
}

@get:StringRes
internal val TestMessageOutcome.detailsNoticeLabel: Int?
    get() = when {
        pairingRemoved -> R.string.home_test_pairing_removed
        result == TestMessageResult.Received -> R.string.pairing_test_received
        result == TestMessageResult.Offline -> R.string.home_test_offline_notice
        result == TestMessageResult.AckTimeout -> R.string.home_test_ack_timeout_notice
        result == TestMessageResult.Uncertain -> R.string.home_test_uncertain_notice
        else -> null
    }

data class TestMessageState(
    val pending: Boolean = false,
    val outcome: TestMessageOutcome? = null,
    val announceReceived: Boolean = false
)
