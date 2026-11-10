package com.buddylabs.pinglide.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.ui.components.TestMessageAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PairingEntryViewModel(
    create: (CoroutineScope) -> PairingEntry,
    sendTest: suspend (String) -> TestMessageOutcome?
) : ViewModel() {
    private val entry = create(viewModelScope)
    val state = entry.state
    private var operation: Job? = null
    val testMessage = TestMessageAction(viewModelScope, sendTest)
    private var requestedOwner: Pair<String, String>? = null
    private var requestedGeneration = 0L
    private var cameraRequestId = 0L
    private var requestedHost: PairingHost? = null
    private var testAuthority: Pair<Pair<String, String>?, Long>? = null

    init {
        viewModelScope.launch {
            state.collectLatest { current ->
                testMessage.enter(
                    current.activatedPairingId.takeIf {
                        current.page == PairingEntryPage.Success
                    }
                )
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
        testMessage.leave()
        operation?.cancel()
        cancelCameraRequest()
        entry.leave()
    }

    fun freshScan() {
        if (state.value.page == PairingEntryPage.Progress && state.value.problem == null) return
        operation?.cancel()
        entry.freshScan()
    }

    fun foreground(home: HomeState? = null) {
        if (home != null) {
            val authority = home.owner to home.generation
            if (testAuthority != null && testAuthority != authority) testMessage.leave()
            testAuthority = authority
            testMessage.enter(
                state.value.activatedPairingId.takeIf {
                    state.value.page == PairingEntryPage.Success
                }
            )
        }
        run { entry.foreground() }
    }

    fun cameraFailed() = entry.cameraFailed()

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
