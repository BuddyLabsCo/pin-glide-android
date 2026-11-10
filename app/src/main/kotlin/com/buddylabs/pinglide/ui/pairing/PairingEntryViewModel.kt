package com.buddylabs.pinglide.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PairingEntryViewModel(
    create: (CoroutineScope) -> PairingEntry,
    private val sendTest: suspend (String) -> TestMessageOutcome?
) : ViewModel() {
    private val entry = create(viewModelScope)
    val state = entry.state
    private var operation: Job? = null
    private var testOperation: Job? = null
    private val mutableTestState = MutableStateFlow(PairingTestState())
    val testState = mutableTestState.asStateFlow()
    private var requestedOwner: Pair<String, String>? = null
    private var requestedGeneration = 0L
    private var cameraRequestId = 0L
    private var requestedHost: PairingHost? = null

    init {
        viewModelScope.launch {
            state.collectLatest { current ->
                if (current.page != PairingEntryPage.Success) {
                    testOperation?.cancel()
                    mutableTestState.value = PairingTestState()
                }
                if (current.duplicatePairingId != null) {
                    delay((current.highlightUntil - System.currentTimeMillis()).coerceAtLeast(0))
                    if (state.value.highlightUntil == current.highlightUntil) entry.clearHighlight()
                }
            }
        }
    }

    fun requestCamera(host: PairingHost, home: HomeState): Long {
        cameraRequestId++
        requestedHost = host
        requestedOwner = home.owner
        requestedGeneration = home.generation
        return cameraRequestId
    }

    fun isCameraRequestCurrent(requestId: Long, home: HomeState): Boolean =
        requestedHost != null && requestId == cameraRequestId &&
            home.owner == requestedOwner && home.generation == requestedGeneration

    fun cancelCameraRequest() {
        cameraRequestId++
        requestedHost = null
        requestedOwner = null
    }

    fun cameraGranted(requestId: Long, home: HomeState) {
        if (!isCameraRequestCurrent(requestId, home)) return
        val host = requestedHost ?: return
        cancelCameraRequest()
        run { entry.start(host) }
    }

    fun scan(text: String, defaultAlias: String) = run { entry.scan(text, defaultAlias) }

    fun editAlias(alias: String) = entry.editAlias(alias)

    fun confirm() = run { entry.confirm() }

    fun back() {
        if (state.value.page == PairingEntryPage.Progress && state.value.problem == null) return
        operation?.cancel()
        if (state.value.host == PairingHost.Onboarding &&
            state.value.page == PairingEntryPage.Consent
        ) {
            entry.returnToScanner()
        } else {
            close()
        }
    }

    fun close() {
        if (state.value.page == PairingEntryPage.Progress && state.value.problem == null) return
        abandon()
    }

    fun abandon() {
        testOperation?.cancel()
        mutableTestState.value = PairingTestState()
        operation?.cancel()
        cancelCameraRequest()
        entry.leave()
    }

    fun freshScan() {
        if (state.value.page == PairingEntryPage.Progress && state.value.problem == null) return
        operation?.cancel()
        entry.freshScan()
    }

    fun foreground() = run { entry.foreground() }

    fun cameraFailed() = entry.cameraFailed()

    fun testMessage() {
        val target = state.value.activatedPairingId ?: return
        if (state.value.page != PairingEntryPage.Success || testOperation?.isActive == true ||
            testState.value.announceReceived
        ) {
            return
        }
        mutableTestState.value = PairingTestState(pending = true)
        testOperation = viewModelScope.launch {
            val result = sendTest(target)
            if (state.value.activatedPairingId == target &&
                state.value.page == PairingEntryPage.Success
            ) {
                mutableTestState.value = PairingTestState(
                    outcome = result,
                    announceReceived = result?.result == TestMessageResult.Received
                )
            }
        }
    }

    fun consumeTestSuccess(): Boolean {
        if (state.value.page != PairingEntryPage.Success || !testState.value.announceReceived) {
            return false
        }
        mutableTestState.value = testState.value.copy(announceReceived = false)
        return true
    }

    fun dismissNotice() = entry.dismissNotice()

    private fun run(action: suspend () -> Unit) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                action()
            } catch (_: IdentityStorageException) {
                entry.leave()
            }
        }
    }

    override fun onCleared() {
        entry.leave()
    }
}

data class PairingTestState(
    val pending: Boolean = false,
    val outcome: TestMessageOutcome? = null,
    val announceReceived: Boolean = false
)
