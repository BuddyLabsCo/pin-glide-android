package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.AuthorizationState

enum class HomeReadiness {
    Revoked,
    LoginRequired,
    SmsRequired,
    Unknown,
    Empty,
    Disabled,
    Offline,
    Ready
}

data class TargetDevice(
    val pairingId: String,
    val alias: String,
    val enabled: Boolean,
    val keyUsable: Boolean,
    val presence: Presence = Presence.Unknown,
    val stateChangedAt: String? = null,
    val lastConfirmedPresence: Presence = presence
)

data class HomeState(
    val targets: List<TargetDevice> = emptyList(),
    val listed: Boolean = false,
    val refreshing: Boolean = false,
    val listRefreshFailed: Boolean = false,
    val selectionWriteFailed: Boolean = false,
    val authorization: AuthorizationState = AuthorizationState.Checking,
    val generation: Long = 0,
    val owner: Pair<String, String>? = null
) {
    val failed: Boolean
        get() = listRefreshFailed || selectionWriteFailed

    private val hasCachedTargetsDuringRefresh: Boolean
        get() = refreshing && !listRefreshFailed && targets.isNotEmpty()

    fun readiness(smsGranted: Boolean): HomeReadiness {
        val enabled = targets.filter { it.enabled && it.keyUsable }
        return when {
            authorization == AuthorizationState.RegistrationRevoked -> HomeReadiness.Revoked

            authorization == AuthorizationState.LoginRequired ||
                authorization == AuthorizationState.RecoveryRequired -> HomeReadiness.LoginRequired

            !smsGranted -> HomeReadiness.SmsRequired

            authorization != AuthorizationState.Ready -> HomeReadiness.Unknown

            !listed && !hasCachedTargetsDuringRefresh -> HomeReadiness.Unknown

            targets.isEmpty() -> HomeReadiness.Empty

            enabled.isEmpty() -> HomeReadiness.Disabled

            enabled.all { it.presence == Presence.Offline } -> HomeReadiness.Offline

            else -> HomeReadiness.Ready
        }
    }
}
