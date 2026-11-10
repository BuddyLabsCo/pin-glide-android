package com.buddylabs.pinglide.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PairingEntryViewModel(create: (CoroutineScope) -> PairingEntry) : ViewModel() {
    private val entry = create(viewModelScope)
    val state = entry.state
    private var operation: Job? = null
    private var requestedOwner: Pair<String, String>? = null
    private var requestedGeneration = 0L
    var requestedHost: PairingHost? = null
        private set

    init {
        viewModelScope.launch {
            state.collectLatest { current ->
                if (current.duplicatePairingId != null) {
                    delay((current.highlightUntil - System.currentTimeMillis()).coerceAtLeast(0))
                    if (state.value.highlightUntil == current.highlightUntil) entry.clearHighlight()
                }
            }
        }
    }

    fun requestCamera(host: PairingHost, home: HomeState) {
        requestedHost = host
        requestedOwner = home.owner
        requestedGeneration = home.generation
    }

    fun cameraGranted(home: HomeState) {
        val host = requestedHost ?: return
        requestedHost = null
        if (home.owner != requestedOwner || home.generation != requestedGeneration) return
        run { entry.start(host) }
    }

    fun scan(text: String, defaultAlias: String) = run { entry.scan(text, defaultAlias) }

    fun editAlias(alias: String) = entry.editAlias(alias)

    fun confirm() = run { entry.confirm() }

    fun back() {
        operation?.cancel()
        entry.scannerBack()
    }

    fun close() {
        operation?.cancel()
        requestedHost = null
        entry.leave()
    }

    fun freshScan() {
        operation?.cancel()
        entry.freshScan()
    }

    fun foreground() = run { entry.foreground() }

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
