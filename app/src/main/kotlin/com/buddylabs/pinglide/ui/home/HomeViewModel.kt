package com.buddylabs.pinglide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SmsRecovery { Idle, Pending, Denied, Granted, SettingsUnavailable }

class HomeViewModel(private val pairings: OwnPairings, private val restoration: SenderRestoration) :
    ViewModel() {
    val state = pairings.state
    val loginRestoration = restoration.state
    private var loginOperation: Job? = null
    private val mutableAction = MutableStateFlow(TargetAction())
    val action = mutableAction.asStateFlow()
    private val mutableSmsRecovery = MutableStateFlow(SmsRecovery.Idle)
    val smsRecovery = mutableSmsRecovery.asStateFlow()
    private val mutablePairingEntryOpen = MutableStateFlow(false)
    val pairingEntryOpen = mutablePairingEntryOpen.asStateFlow()
    private var recoveryGeneration = 0L

    init {
        viewModelScope.launch {
            state.collect { current ->
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
        restoration.leave()
        loginOperation?.cancel()
    }

    fun dismissLoginNotice() = restoration.dismissNotice()

    override fun onCleared() {
        restoration.cancel()
    }

    fun openPairingEntry() {
        recoveryGeneration++
        mutableSmsRecovery.value = SmsRecovery.Idle
        mutablePairingEntryOpen.value = true
    }

    fun closePairingEntry() {
        mutablePairingEntryOpen.value = false
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
            if (result != PairingChange.InvalidAlias) pairings.synchronize()
            if (action.value !== pending) return@launch
            val current = state.value
            val reconciled = if (removing && result == PairingChange.Unavailable &&
                current.listed && !current.failed &&
                current.targets.none { it.pairingId == pairingId }
            ) {
                PairingChange.Removed
            } else {
                result
            }
            mutableAction.value = if (current.owner == expected.owner) {
                pending.copy(pending = false, result = reconciled)
            } else {
                TargetAction()
            }
        }
    }
}

data class TargetAction(
    val pairingId: String? = null,
    val owner: Pair<String, String>? = null,
    val pending: Boolean = false,
    val removing: Boolean = false,
    val result: PairingChange? = null
)
