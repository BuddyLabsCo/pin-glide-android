package com.buddylabs.pinglide.domain.identity

import com.buddylabs.pinglide.data.storage.loggedOut
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitLoginTest {
    @Test
    fun laterLogoutWinsOverAnAlreadyAcceptedLoginCompletion() = withLogin {
        store.afterAccept = {
            login.leaveAccount()
            renewal.logout(store).await()
        }
        login.restore()
        login.beginRegistration()

        login.login()

        assertEquals(LoginState.Welcome, login.state.value)
        assertEquals(AuthorizationState.LoggedOut, renewal.state.value)
        assertEquals(RefreshState.LoggedOut, store.read().refreshState)
        assertEquals(null, store.read().identity)
    }

    @Test
    fun sameAccountCannotSilentlyReplaceItsRetainedSender() = withLogin {
        response = identity("account", "unexpected-sender")
        login.restore()
        login.beginRegistration()
        val original = store.read()

        login.login()

        assertSame(original, store.read())
        assertEquals(LoginState.RestorationError, login.state.value)
    }

    @Test
    fun failedRestorationRequiresAnotherAuthorizationBeforeNewEnrollment() = withLogin {
        serviceFailure = LoginServiceException()
        login.restore()
        login.beginRegistration()
        val original = store.read()

        login.login()

        assertEquals(LoginState.RestorationError, login.state.value)
        assertSame(original, store.read())
        assertEquals(listOf("fresh-1:Phone:sender"), requests)

        serviceFailure = null
        response = identity("other-account", "other-sender")
        login.login(registerNew = true)

        assertSame(response, store.read().identity)
        assertEquals(listOf("fresh-1:Phone:sender", "fresh-2:Phone:null"), requests)
    }

    @Test
    fun failedReplacementCommitPreservesTheLoggedOutContext() = withLogin {
        response = identity("other-account", "other-sender")
        store.failAccept = true
        login.restore()
        login.beginRegistration()
        val original = store.read()

        login.login()

        assertSame(original, store.read())
        assertEquals(LoginState.StorageError, login.state.value)
        assertEquals(AuthorizationState.LoginRequired, renewal.state.value)
    }

    @Test
    fun cancellationDuringPersistencePreventsTheLateCommit() = withLogin {
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        store.beforeAccept = {
            entered.complete(Unit)
            resume.await()
        }
        login.restore()
        login.beginRegistration()
        val original = store.read()
        val pending = scope.async(start = CoroutineStart.UNDISPATCHED) { login.login() }
        entered.await()

        login.cancel()
        resume.complete(Unit)
        pending.await()

        assertSame(original, store.read())
        assertEquals(LoginState.Entry, login.state.value)
        assertEquals(AuthorizationState.LoginRequired, renewal.state.value)
    }

    @Test
    fun knownClosedSenderCannotBeRestored() = withLogin {
        store.snapshot = store.snapshot.copy(closedSenderIds = setOf("sender"))
        login.restore()
        login.beginRegistration()
        val original = store.read()

        login.login()

        assertSame(original, store.read())
        assertEquals(LoginState.RestorationError, login.state.value)
    }

    @Test
    fun cancelledQqAuthorizationReturnsToEntryAndKeepsTheRestorationContext() = withLogin {
        authorizationFailure = QqAuthorizationException(cancelled = true)
        login.restore()
        login.beginRegistration()
        val original = store.read()

        login.login()

        assertEquals(LoginState.Entry, login.state.value)
        assertSame(original, store.read())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun cancellationAfterDurableAcceptanceStillResumesTheCommittedSession() = withLogin {
        store.afterAccept = { throw CancellationException("Cancelled dispatcher return") }
        login.restore()
        login.beginRegistration()

        val failure = runCatching { login.login() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertSame(response, store.read().identity)
        assertEquals(LoginState.FirstDevice, login.state.value)
        assertEquals(AuthorizationState.Ready, renewal.state.value)
    }

    @Test
    fun verifiedDifferentAccountCanReplaceTheLoggedOutSender() = withLogin {
        response = identity("other-account", "other-sender")
        login.restore()
        assertEquals(LoginState.Welcome, login.state.value)
        login.beginRegistration()

        login.login()

        assertSame(response, store.read().identity)
        assertEquals(LoginState.FirstDevice, login.state.value)
        assertEquals(AuthorizationState.Ready, renewal.state.value)
        assertEquals(listOf("fresh-1:Phone:sender"), requests)
    }

    private fun withLogin(test: suspend Session.() -> Unit) = runBlocking {
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
        val store = MemoryStore(
            IdentitySnapshot(
                generation = 2,
                refreshState = RefreshState.LoggedOut,
                restoration = SenderContext("account", "sender")
            )
        )
        val renewal = CredentialRenewal(
            store,
            RenewalService { _, _ -> error("Unexpected renewal") },
            scope,
            Clock.fixed(now, ZoneOffset.UTC)
        )
        var response = identity("account", "sender")
        val requests = mutableListOf<String>()
        var authorizationFailure: QqAuthorizationException? = null
        var serviceFailure: LoginServiceException? = null
        private var authorizations = 0
        val login = ExplicitLogin(
            store,
            QqAuthorization {
                authorizationFailure?.let { throw it }
                "fresh-${++authorizations}"
            },
            LoginService { code, name, sender ->
                requests += "$code:$name:$sender"
                serviceFailure?.let { throw it }
                response
            },
            renewal,
            { "Phone" },
            Clock.fixed(now, ZoneOffset.UTC)
        )

        fun identity(account: String, sender: String) = SenderIdentity(
            account,
            sender,
            ManagementCredential("management", now.plusSeconds(3_600).toString()),
            UploadCredential("upload", now.plusSeconds(3_600).toString()),
            RefreshCredential("refresh", now.plusSeconds(86_400).toString())
        )
    }

    private class MemoryStore(var snapshot: IdentitySnapshot) :
        IdentityStore,
        LogoutStore {
        var failAccept = false
        var beforeAccept: suspend () -> Unit = {}
        var afterAccept: suspend () -> Unit = {}

        override suspend fun read(): IdentitySnapshot = snapshot

        override suspend fun logout(): RefreshCredential? {
            val refresh = snapshot.identity?.refresh
            snapshot = snapshot.loggedOut()
            return refresh
        }

        override suspend fun updateRenewal(
            expected: IdentitySnapshot,
            replacement: IdentitySnapshot,
            attempt: LoginAttempt
        ): Boolean = error("Unexpected renewal write")

        override suspend fun accept(
            expectedGeneration: Long,
            identity: SenderIdentity,
            attempt: LoginAttempt
        ): Boolean {
            if (snapshot.generation != expectedGeneration) return false
            if (failAccept) throw IdentityStorageException()
            beforeAccept()
            val accepted = attempt.commit {
                snapshot = snapshot.copy(
                    generation = snapshot.generation + 1,
                    identity = identity,
                    refreshState = RefreshState.Active,
                    restoration = null
                )
            }
            if (accepted) afterAccept()
            return accepted
        }
    }
}
