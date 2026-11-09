package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.RenewalReply
import com.buddylabs.pinglide.domain.identity.RenewalService
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnPairingsTest {
    @Test
    fun currentSwitchOffSurvivesRenewalAndPendingListReconciliation() = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(coroutineContext + Job())
            try {
                val store = MemoryStore(identity("initial", now.plusSeconds(60)))
                val renewal = renewal(store, scope)
                val requested = CompletableDeferred<Unit>()
                val response = CompletableDeferred<Unit>()
                val home = OwnPairings(
                    store,
                    PairingService {
                        requested.complete(Unit)
                        response.await()
                        listOf(
                            RemotePairing(
                                "pairing",
                                "receiver",
                                "Desk",
                                Presence.Online,
                                now.toString()
                            )
                        )
                    },
                    renewal,
                    scope
                )
                val refresh = launch { home.refresh() }
                requested.await()
                home.state.first { it.authorization == AuthorizationState.Ready && it.refreshing }
                assertEquals(2L, store.read().generation)

                home.select(home.state.value, "pairing", false)

                assertFalse(home.state.value.targets.single().enabled)
                response.complete(Unit)
                refresh.join()
                assertFalse(home.state.value.targets.single().enabled)
                assertFalse(store.readPairings().items.single().enabled)
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun obsoleteRowsCannotOverwriteNewerPreferences() = withHome { home, store ->
        val obsolete = home.state.value
        home.select(obsolete, "pairing", false)
        home.select(obsolete, "pairing", true)
        assertFalse(home.state.value.targets.single().enabled)

        val displayed = home.state.value
        assertTrue(store.select(store.readPairings(), "pairing", true))
        home.select(displayed, "pairing", false)

        assertTrue(home.state.value.targets.single().enabled)
        assertTrue(home.state.value.failed)
        assertTrue(store.readPairings().items.single().enabled)
    }

    @Test
    fun changedAccountOrSenderCannotRebaseAnOldSelection() {
        for ((accountId, senderId) in listOf("other" to "sender", "account" to "other")) {
            withHome { home, store ->
                assertTrue(
                    store.accept(
                        store.read().generation,
                        identity("replacement", now.plusSeconds(3_600), accountId, senderId),
                        LoginAttempt()
                    )
                )

                home.select(home.state.value, "pairing", false)

                assertTrue(store.readPairings().items.single().enabled)
                assertTrue(home.state.value.failed)
            }
        }
    }

    @Test
    fun revokedAuthorityCannotRebaseAnOldSelection() = withHome { home, store ->
        val authority = store.read()
        assertTrue(
            store.updateRenewal(
                authority,
                IdentitySnapshot(
                    authority.generation + 1,
                    authority.identity,
                    refreshState = RefreshState.RegistrationRevoked
                )
            )
        )

        home.select(home.state.value, "pairing", false)

        assertTrue(store.readPairings().items.single().enabled)
        assertTrue(home.state.value.failed)
    }

    private fun withHome(action: suspend (OwnPairings, MemoryStore) -> Unit) = runBlocking {
        withTimeout(5_000) {
            val scope = CoroutineScope(coroutineContext + Job())
            try {
                val store = MemoryStore(identity("initial", now.plusSeconds(3_600)))
                val home = OwnPairings(
                    store,
                    PairingService {
                        listOf(
                            RemotePairing(
                                "pairing",
                                "receiver",
                                "Desk",
                                Presence.Online,
                                now.toString()
                            )
                        )
                    },
                    renewal(store, scope),
                    scope
                )
                home.refresh()
                action(home, store)
            } finally {
                scope.cancel()
            }
        }
    }

    private fun renewal(store: MemoryStore, scope: CoroutineScope): CredentialRenewal =
        CredentialRenewal(
            store,
            RenewalService { _, operation ->
                RenewalReply(
                    operation.operationId,
                    operation.attempt.toLong(),
                    identity("renewed", now.plusSeconds(3_600))
                )
            },
            scope,
            Clock.fixed(now, ZoneOffset.UTC)
        )

    private class MemoryStore(initial: SenderIdentity) :
        IdentityStore,
        PairingStore {
        private var authority = IdentitySnapshot(1, initial)
        private var revision = 1L
        private var items = listOf(OwnPairing("pairing", "receiver", "Desk", true, ByteArray(32)))

        override suspend fun read(): IdentitySnapshot = authority

        override suspend fun updateRenewal(
            expected: IdentitySnapshot,
            replacement: IdentitySnapshot
        ): Boolean {
            if (authority !== expected) return false
            authority = replacement
            return true
        }

        override suspend fun accept(
            expectedGeneration: Long,
            identity: SenderIdentity,
            attempt: LoginAttempt
        ): Boolean {
            if (authority.generation != expectedGeneration) return false
            return attempt.commit { authority = IdentitySnapshot(expectedGeneration + 1, identity) }
        }

        override suspend fun readPairings(): PairingSnapshot =
            PairingSnapshot(authority, revision, true, items)

        override suspend fun reconcile(
            expected: PairingSnapshot,
            items: List<RemotePairing>
        ): Boolean {
            if (!matches(expected)) return false
            this.items = items.map { remote ->
                this.items.single { it.pairingId == remote.pairingId }.copy(alias = remote.alias)
            }
            revision++
            return true
        }

        override suspend fun select(
            expected: PairingSnapshot,
            pairingId: String,
            enabled: Boolean
        ): Boolean {
            if (!matches(expected)) return false
            items = items.map { if (it.pairingId == pairingId) it.copy(enabled = enabled) else it }
            revision++
            return true
        }

        private fun matches(expected: PairingSnapshot): Boolean =
            authority.generation == expected.authority.generation &&
                authority.refreshState == expected.authority.refreshState &&
                authority.identity?.accountId == expected.authority.identity?.accountId &&
                authority.identity?.senderId == expected.authority.identity?.senderId &&
                revision == expected.revision
    }

    private fun identity(
        token: String,
        expiresAt: Instant,
        accountId: String = "account",
        senderId: String = "sender"
    ): SenderIdentity = SenderIdentity(
        accountId,
        senderId,
        ManagementCredential("management-$token", expiresAt.toString()),
        UploadCredential("upload-$token", now.plusSeconds(3_600).toString()),
        RefreshCredential("refresh-$token", now.plusSeconds(86_400).toString())
    )

    private val now = Instant.parse("2026-11-09T12:00:00Z")
}
