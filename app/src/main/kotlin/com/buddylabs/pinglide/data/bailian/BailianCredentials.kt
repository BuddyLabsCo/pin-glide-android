package com.buddylabs.pinglide.data.bailian

import com.buddylabs.pinglide.data.service.ModelAccessRejectedException
import com.buddylabs.pinglide.data.service.ProviderCredentialService
import com.buddylabs.pinglide.data.storage.ProviderCredentialCache
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.generated.http.model.ModelCredential
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BailianCredentials(
    private val store: IdentityStore,
    private val renewal: CredentialRenewal,
    private val service: ProviderCredentialService,
    private val cache: ProviderCredentialCache,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) {
    private val gate = Mutex()
    private var flight: Deferred<ProviderLease>? = null
    private var flightSession = -1L

    internal suspend fun acquire(session: Long): ProviderLease {
        check(renewal.isSessionCurrent(session))
        val authority = renewal.authorize(CredentialAccess.Upload)
        check(renewal.isSessionCurrent(session))
        val pending = gate.withLock {
            val cached = cache.read(authority.generation, session)
            if (cached != null &&
                Instant.parse(cached.credential.expiresAt).minusSeconds(30).isAfter(clock.instant())
            ) {
                return ProviderLease(cached, authority.generation, session, cache.revision).also {
                    check(isCurrent(it))
                }
            }
            flight?.takeIf {
                !it.isCompleted && flightSession == session
            } ?: scope.async {
                try {
                    issue(session)
                } finally {
                    gate.withLock {
                        if (flightSession == session) flight = null
                    }
                }
            }.also {
                flight = it
                flightSession = session
            }
        }
        return pending.await().also { check(isCurrent(it)) }
    }

    private suspend fun issue(session: Long): ProviderLease {
        val lease = renewal.withAccess(CredentialAccess.Upload) { identity ->
            check(renewal.isSessionCurrent(session))
            val authority = store.read()
            check(authority.identity?.upload?.token == identity.upload.token)
            val revision = cache.revision
            val value = try {
                service.issue(identity)
            } catch (rejected: ModelAccessRejectedException) {
                renewal.rejectAccess(identity, RefreshState.LoginRequired, session)
                throw rejected
            }
            value.validate(clock.instant())
            ProviderLease(value, authority.generation, session, revision)
        }
        check(isCurrent(lease))
        check(cache.write(lease.value, lease.generation, session, lease.revision))
        return lease
    }

    internal suspend fun isCurrent(lease: ProviderLease): Boolean {
        if (!renewal.isSessionCurrent(lease.session) || lease.revision != cache.revision) {
            return false
        }
        val current = store.read()
        return current.generation == lease.generation &&
            current.refreshState == RefreshState.Active &&
            current.identity?.let { !renewal.isRegistrationClosed(it) } == true &&
            renewal.isSessionCurrent(lease.session) && lease.revision == cache.revision
    }

    internal suspend fun invalidate(lease: ProviderLease) {
        gate.withLock {
            val current = cache.read(lease.generation, lease.session)
            if (current?.credential?.apiKey == lease.value.credential.apiKey) cache.clear()
        }
    }
}

internal class ProviderLease(
    val value: ModelCredential,
    val generation: Long,
    val session: Long,
    val revision: Long
)
