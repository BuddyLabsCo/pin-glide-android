package com.buddylabs.pinglide.ui.components

import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    fun send() {
        val target = pairingId ?: return
        if (operation?.isActive == true || state.value.announceReceived) return
        val expected = epoch
        mutableState.value = TestMessageState(pending = true)
        operation = scope.launch {
            val result = sendTest(target)
            if (expected == epoch) {
                mutableState.value = TestMessageState(
                    outcome = result,
                    announceReceived = result?.result == TestMessageResult.Received
                )
            }
        }
    }

    fun consumeSuccess(): Boolean {
        if (!state.value.announceReceived) return false
        mutableState.value = state.value.copy(announceReceived = false)
        return true
    }
}

data class TestMessageState(
    val pending: Boolean = false,
    val outcome: TestMessageOutcome? = null,
    val announceReceived: Boolean = false
)
