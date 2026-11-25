package com.buddylabs.pinglide.ui.home

import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePairingStoreTest {
    private val existing =
        RemotePairing("existing", "existing-receiver", "Existing", Presence.Online, "")
    private val pending = OwnPairing(
        "pairing",
        "receiver",
        "New device",
        key = ByteArray(32),
        provisionalSessionId = "session"
    )
    private val store =
        HomePairingStore(listOf(existing), Instant.now().plusSeconds(3600).toString())

    @Test
    fun rejectsInvalidOrDuplicateProvisionalPairingsWithoutChangingState() = runBlocking {
        assertTrue(store.saveProvisional(store.readPairings(), pending))
        val snapshot = store.readPairings()
        val invalid = listOf(
            pending,
            pending.copy(pairingId = existing.pairingId),
            pending.copy(pairingId = "new", receiverId = existing.receiverId),
            pending.copy(pairingId = "new", provisionalSessionId = null),
            pending.copy(pairingId = "new", provisionalSessionId = ""),
            pending.copy(pairingId = "new", key = null),
            pending.copy(pairingId = "new", key = ByteArray(31)),
            pending.copy(pairingId = "new", alias = ""),
            pending.copy(pairingId = "new", encryptions = 1)
        )
        invalid.forEach { pairing ->
            val failure = runCatching { store.saveProvisional(snapshot, pairing) }.exceptionOrNull()
            assertTrue(failure is IdentityStorageException)
        }
        val after = store.readPairings()
        assertEquals(snapshot.revision, after.revision)
        assertEquals(snapshot.items, after.items)
        assertEquals(listOf(pending), after.provisional)
    }

    @Test
    fun rejectsMismatchedReceiverBeforePromotingProvisionalPairing() = runBlocking {
        assertTrue(store.saveProvisional(store.readPairings(), pending))
        val snapshot = store.readPairings()
        val wrongReceiver =
            RemotePairing(pending.pairingId, "wrong", pending.alias, Presence.Online, "")
        val failure = runCatching {
            store.reconcile(snapshot, listOf(wrongReceiver))
        }.exceptionOrNull()
        assertTrue(failure is IdentityStorageException)
        val after = store.readPairings()
        assertEquals(snapshot.revision, after.revision)
        assertEquals(snapshot.items, after.items)
        assertEquals(listOf(pending), after.provisional)

        val matching =
            RemotePairing(pending.pairingId, pending.receiverId, pending.alias, Presence.Online, "")
        assertTrue(store.reconcile(after, listOf(existing, matching)))
        val active = store.readPairings()
        assertEquals(pending.copy(provisionalSessionId = null), active.items.last())
        assertTrue(active.provisional.isEmpty())
    }
}
