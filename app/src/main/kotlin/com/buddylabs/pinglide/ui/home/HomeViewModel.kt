package com.buddylabs.pinglide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SmsRecovery { Idle, Pending, Denied, Granted, SettingsUnavailable }

class HomeViewModel(private val pairings: OwnPairings) : ViewModel() {
    val state = pairings.state
    private val mutableSmsRecovery = MutableStateFlow(SmsRecovery.Idle)
    val smsRecovery = mutableSmsRecovery.asStateFlow()

    fun beginSmsRecovery(): Boolean {
        if (smsRecovery.value == SmsRecovery.Pending) return false
        mutableSmsRecovery.value = SmsRecovery.Pending
        return true
    }

    fun finishSmsRecovery(granted: Boolean) {
        if (smsRecovery.value != SmsRecovery.Pending) return
        if (granted) {
            viewModelScope.launch {
                pairings.refresh()
                mutableSmsRecovery.value = SmsRecovery.Granted
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
}
