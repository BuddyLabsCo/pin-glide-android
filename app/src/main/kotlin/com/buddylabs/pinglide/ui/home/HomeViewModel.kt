package com.buddylabs.pinglide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SmsRecovery { Idle, Pending, Denied, Granted, SettingsUnavailable }

class HomeViewModel(private val pairings: OwnPairings) : ViewModel() {
    val state = pairings.state
    private val mutableAction = MutableStateFlow(TargetAction())
    val action = mutableAction.asStateFlow()
    private val mutableSmsRecovery = MutableStateFlow(SmsRecovery.Idle)
    val smsRecovery = mutableSmsRecovery.asStateFlow()
    private val mutablePairingEntryOpen = MutableStateFlow(false)
    val pairingEntryOpen = mutablePairingEntryOpen.asStateFlow()
    private var recoveryGeneration = 0L

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
        change(expected, pairingId) { pairings.remove(expected, pairingId) }
    }

    private fun change(
        expected: HomeState,
        pairingId: String,
        operation: suspend () -> PairingChange
    ) {
        if (action.value.pending) return
        val pending = TargetAction(pairingId, expected.owner, pending = true)
        mutableAction.value = pending
        viewModelScope.launch {
            val result = operation()
            mutableAction.value = if (state.value.owner == expected.owner) {
                pending.copy(pending = false, result = result)
            } else {
                TargetAction()
            }
            if (result != PairingChange.InvalidAlias) pairings.refresh()
        }
    }
}

data class TargetAction(
    val pairingId: String? = null,
    val owner: Pair<String, String>? = null,
    val pending: Boolean = false,
    val result: PairingChange? = null
)
