package com.buddylabs.pinglide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import kotlinx.coroutines.launch

class HomeViewModel(private val pairings: OwnPairings) : ViewModel() {
    val state = pairings.state

    fun refresh() {
        viewModelScope.launch { pairings.refresh() }
    }

    fun select(expected: HomeState, pairingId: String, enabled: Boolean) {
        viewModelScope.launch { pairings.select(expected, pairingId, enabled) }
    }
}
