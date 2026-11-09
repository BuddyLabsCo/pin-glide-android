package com.buddylabs.pinglide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val pairings: OwnPairings) : ViewModel() {
    val state = pairings.state
    private val mutableAction = MutableStateFlow(TargetAction())
    val action = mutableAction.asStateFlow()

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
