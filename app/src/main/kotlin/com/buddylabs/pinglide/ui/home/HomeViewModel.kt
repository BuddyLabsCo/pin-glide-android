package com.buddylabs.pinglide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.ui.components.TestMessageAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SmsRecovery { Idle, Pending, Denied, Granted, SettingsUnavailable }

class HomeViewModel(
    private val pairings: OwnPairings,
    private val restoration: SenderRestoration,
    sendTest: suspend (String) -> TestMessageOutcome?
) : ViewModel() {
    val state = pairings.state
    val loginRestoration = restoration.state
    private var loginOperation: Job? = null
    private val mutableAction = MutableStateFlow(TargetAction())
    val action = mutableAction.asStateFlow()
    private val mutableSmsRecovery = MutableStateFlow(SmsRecovery.Idle)
    val smsRecovery = mutableSmsRecovery.asStateFlow()
    private var recoveryGeneration = 0L
    val testMessage = TestMessageAction(viewModelScope, sendTest)
    private var testOrigin: HomeState? = null
    private val mutableSettingsOpen = MutableStateFlow(false)
    val settingsOpen = mutableSettingsOpen.asStateFlow()

    fun showSettings(open: Boolean) {
        mutableSettingsOpen.value = open
    }

    init {
        viewModelScope.launch {
            state.collect { current ->
                if (current.authorization in setOf(
                        AuthorizationState.LoggedOut,
                        AuthorizationState.RegistrationClosed,
                        AuthorizationState.RegistrationRevoked
                    )
                ) {
                    showSettings(false)
                }
                val origin = testOrigin
                val target = testMessage.pairingId
                val original = origin?.targets?.find { it.pairingId == target }
                val selected = current.targets.find { it.pairingId == target }
                if (origin != null && (
                        origin.owner != current.owner ||
                            origin.generation != current.generation ||
                            selected?.keyUsable != true || selected.alias != original?.alias
                        )
                ) {
                    showTestTarget(null)
                }
                val previous = action.value
                if (previous.owner != current.owner) {
                    mutableAction.value = TargetAction()
                } else if (!previous.pending && previous.removing &&
                    previous.result == PairingChange.Unavailable && current.listed &&
                    !current.refreshing && !current.failed &&
                    current.targets.none { it.pairingId == previous.pairingId }
                ) {
                    mutableAction.value = previous.copy(result = PairingChange.Removed)
                }
            }
        }
    }

    fun restoreLogin() {
        showTestTarget(null)
        if (loginOperation?.isActive == true) return
        loginOperation = viewModelScope.launch {
            try {
                restoration.restore()
            } finally {
                if (loginRestoration.value == RestorationState.Restored ||
                    loginRestoration.value == RestorationState.Idle
                ) {
                    withContext(NonCancellable) { pairings.synchronize() }
                }
            }
        }
    }

    fun cancelLogin() {
        restoration.cancel()
        loginOperation?.cancel()
    }

    fun leaveLogin() {
        showTestTarget(null)
        restoration.leave()
        restoration.dismissNotice()
        loginOperation?.cancel()
        recoveryGeneration++
        mutableSmsRecovery.value = SmsRecovery.Idle
        mutableAction.value = TargetAction()
    }

    fun dismissLoginNotice() = restoration.dismissNotice()

    override fun onCleared() {
        restoration.cancel()
    }

    fun beginPairing() {
        showTestTarget(null)
        recoveryGeneration++
        mutableSmsRecovery.value = SmsRecovery.Idle
    }

    fun beginSmsRecovery(): Boolean {
        if (smsRecovery.value == SmsRecovery.Pending) return false
        mutableSmsRecovery.value = SmsRecovery.Pending
        return true
    }

    fun finishSmsRecovery(granted: Boolean) {
        if (smsRecovery.value != SmsRecovery.Pending) return
        if (granted) {
            val expectedGeneration = recoveryGeneration
            viewModelScope.launch {
                pairings.refresh()
                if (recoveryGeneration == expectedGeneration) {
                    mutableSmsRecovery.value = SmsRecovery.Granted
                }
            }
        } else {
            mutableSmsRecovery.value = SmsRecovery.Denied
        }
    }

    fun smsSettingsUnavailable() {
        mutableSmsRecovery.value = SmsRecovery.SettingsUnavailable
    }

    fun dismissSmsFeedback(expected: SmsRecovery) {
        if (smsRecovery.value == expected) mutableSmsRecovery.value = SmsRecovery.Idle
    }

    fun refresh() {
        viewModelScope.launch { pairings.refresh() }
    }

    fun select(expected: HomeState, pairingId: String, enabled: Boolean) {
        viewModelScope.launch { pairings.select(expected, pairingId, enabled) }
    }

    fun clearAction() {
        if (!action.value.pending) mutableAction.value = TargetAction()
    }

    fun showTestTarget(pairingId: String?) {
        val current = state.value
        if (testOrigin?.owner != current.owner || testOrigin?.generation != current.generation) {
            testMessage.leave()
        }
        testOrigin = current.takeIf { pairingId != null }
        testMessage.enter(pairingId)
    }

    fun rename(expected: HomeState, pairingId: String, alias: String) {
        change(expected, pairingId) { pairings.rename(expected, pairingId, alias) }
    }

    fun remove(expected: HomeState, pairingId: String) {
        change(expected, pairingId, removing = true) { pairings.remove(expected, pairingId) }
    }

    private fun change(
        expected: HomeState,
        pairingId: String,
        removing: Boolean = false,
        operation: suspend () -> PairingChange
    ) {
        if (action.value.pending) return
        val pending = TargetAction(pairingId, expected.owner, pending = true, removing = removing)
        mutableAction.value = pending
        viewModelScope.launch {
            val result = operation()
            if (action.value !== pending) return@launch
            val completed = state.value
            if (result != PairingChange.InvalidAlias) pairings.synchronize()
            if (action.value !== pending) return@launch
            val current = state.value
            mutableAction.value = if (current.owner == expected.owner &&
                current.generation == completed.generation
            ) {
                pending.copy(
                    pending = false,
                    result = pending.reconcileResult(result, completed, current)
                )
            } else {
                TargetAction()
            }
        }
    }

    private fun TargetAction.reconcileResult(
        result: PairingChange,
        completed: HomeState,
        current: HomeState
    ): PairingChange = when {
        result == PairingChange.Renamed &&
            current.targets.find { it.pairingId == pairingId }?.alias !=
            completed.targets.find { it.pairingId == pairingId }?.alias -> PairingChange.Unavailable

        removing && result == PairingChange.Unavailable && current.listed && !current.failed &&
            current.targets.none { it.pairingId == pairingId } -> PairingChange.Removed

        else -> result
    }
}

data class TargetAction(
    val pairingId: String? = null,
    val owner: Pair<String, String>? = null,
    val pending: Boolean = false,
    val removing: Boolean = false,
    val result: PairingChange? = null
)
