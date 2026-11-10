package com.buddylabs.pinglide.domain.identity

import com.buddylabs.pinglide.data.storage.loggedOut
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoutRaceTest {
    @Test
    fun currentSessionRevocationStillClosesTheSender() = withSession {
        runCatching { renewal.rejectAccess(identity, RefreshState.RegistrationRevoked) }

        assertEquals(RefreshState.RegistrationRevoked, store.read().refreshState)
        assertEquals(setOf("sender"), store.read().closedSenderIds)
        assertEquals(emptyList<String>(), store.pairingKeys)
        assertEquals(AuthorizationState.RegistrationRevoked, renewal.state.value)
        val restarted = login()
        restarted.restore()
        assertEquals(LoginState.Revoked, restarted.state.value)
    }

    @Test
    fun logoutDuringRevocationReadPreservesRestorationAndWelcome() = withSession {
        val paused = Pause()
        store.readPause = paused
        val rejection = scope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { renewal.rejectAccess(identity, RefreshState.RegistrationRevoked) }
        }
        paused.entered.await()

        val local = renewal.logout(store)
        assertEquals(AuthorizationState.LoggedOut, renewal.state.value)
        paused.resume.complete(Unit)
        rejection.await()
        local.await()

        assertLoggedOut()
    }

    @Test
    fun logoutBeforeRevocationCommitPreservesRestorationAndWelcome() = withSession {
        val paused = Pause()
        store.writePause = paused
        val rejection = scope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { renewal.rejectAccess(identity, RefreshState.RegistrationRevoked) }
        }
        paused.entered.await()

        val local = renewal.logout(store)
        assertEquals(AuthorizationState.LoggedOut, renewal.state.value)
        paused.resume.complete(Unit)
        rejection.await()
        local.await()

        assertLoggedOut()
    }

    @Test
    fun immediateSetupDuringFailedLogoutStaysOnWelcomeUntilRetrySucceeds() = withSession {
        val login = login()
        login.restore()
        val paused = Pause()
        store.logoutPause = paused
        store.failLogout = true
        val local = renewal.logout(store)
        login.leaveAccount()
        assertEquals(LogoutState.Pending, renewal.logoutState.value)

        val setup = scope.async(start = CoroutineStart.UNDISPATCHED) {
            login.beginRegistration()
        }
        paused.entered.await()
        assertEquals(LoginState.Welcome, login.state.value)
        paused.resume.complete(Unit)
        local.await()
        setup.await()
        assertEquals(LoginState.Welcome, login.state.value)
        assertEquals(LogoutState.Failed, renewal.logoutState.value)

        store.failLogout = false
        renewal.logout(store).await()
        assertEquals(LoginState.Welcome, login.state.value)
        assertEquals(LogoutState.Ready, renewal.logoutState.value)
        login.beginRegistration()
        assertEquals(LoginState.Entry, login.state.value)
        assertEquals(listOf("pairing-key"), store.pairingKeys)
    }

    private fun withSession(test: suspend Session.() -> Unit) = runBlocking {
        withTimeout(5_000) {
            val session = Session(CoroutineScope(coroutineContext + Job()))
            try {
                session.test()
            } finally {
                session.scope.cancel()
            }
        }
    }

    private class Session(val scope: CoroutineScope) {
        val now: Instant = Instant.parse("2030-01-01T00:00:00Z")
        val identity = SenderIdentity(
            "account",
            "sender",
            ManagementCredential("management", now.plusSeconds(3_600).toString()),
            UploadCredential("upload", now.plusSeconds(3_600).toString()),
            RefreshCredential("refresh", now.plusSeconds(86_400).toString())
        )
        val store = MemoryStore(IdentitySnapshot(generation = 1, identity = identity))
        val renewal = CredentialRenewal(
            store,
            RenewalService { _, _ -> error("Unexpected renewal") },
            scope,
            Clock.fixed(now, ZoneOffset.UTC)
        )

        fun login() = ExplicitLogin(
            store,
            QqAuthorization { error("Unexpected QQ authorization") },
            LoginService { _, _, _ -> error("Unexpected login") },
            renewal,
            { "Phone" },
            Clock.fixed(now, ZoneOffset.UTC)
        )

        suspend fun assertLoggedOut() {
            val restored = store.read()
            assertEquals(RefreshState.LoggedOut, restored.refreshState)
            assertEquals(null, restored.identity)
            assertEquals(SenderContext("account", "sender"), restored.restoration)
            assertEquals(emptySet<String>(), restored.closedSenderIds)
            assertEquals(listOf("pairing-key"), store.pairingKeys)
            assertEquals(AuthorizationState.LoggedOut, renewal.state.value)
            val restarted = login()
            restarted.restore()
            assertEquals(LoginState.Welcome, restarted.state.value)
        }
    }

    private class Pause {
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        suspend fun await() {
            entered.complete(Unit)
            resume.await()
        }
    }

    private class MemoryStore(var snapshot: IdentitySnapshot) :
        IdentityStore,
        LogoutStore {
        var readPause: Pause? = null
        var writePause: Pause? = null
        var logoutPause: Pause? = null
        var failLogout = false
        var pairingKeys = listOf("pairing-key")

        override suspend fun read(): IdentitySnapshot {
            val current = snapshot
            val paused = readPause
            readPause = null
            paused?.await()
            return current
        }

        override suspend fun logout(): RefreshCredential? {
            val paused = logoutPause
            logoutPause = null
            paused?.await()
            if (failLogout) throw IdentityStorageException()
            if (snapshot.refreshState.closesRegistration) return null
            val credential = snapshot.identity?.refresh
            snapshot = snapshot.loggedOut()
            return credential
        }

        override suspend fun updateRenewal(
            expected: IdentitySnapshot,
            replacement: IdentitySnapshot,
            attempt: LoginAttempt
        ): Boolean {
            if (snapshot != expected) return false
            val paused = writePause
            writePause = null
            paused?.await()
            return attempt.commit {
                snapshot = replacement
                if (replacement.refreshState.closesRegistration) pairingKeys = emptyList()
            }
        }

        override suspend fun accept(
            expectedGeneration: Long,
            identity: SenderIdentity,
            attempt: LoginAttempt
        ): Boolean = error("Unexpected login commit")
    }
}
