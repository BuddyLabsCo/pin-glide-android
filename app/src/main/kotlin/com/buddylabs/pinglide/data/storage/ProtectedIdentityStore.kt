package com.buddylabs.pinglide.data.storage

import android.content.Context
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.LogoutStore
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.RenewalOperation
import com.buddylabs.pinglide.domain.identity.SenderContext
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.platform.crypto.ProtectedSnapshot
import com.buddylabs.pinglide.platform.crypto.SnapshotProtection
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ProtectedIdentityStore(context: Context) :
    IdentityStore,
    LogoutStore,
    PairingStore {
    private val file = File(context.noBackupFilesDir, "identity.snapshot")
    private val logoutMarker = File(context.noBackupFilesDir, "identity.logged-out")
    private val protection = SnapshotProtection()
    private val writer = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readPairings(): PairingSnapshot = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, authority, alias) = load()
                if (authority.refreshState == RefreshState.LoggedOut) {
                    return@storageOperation PairingSnapshot(authority)
                }
                val current = pairings(document, authority)
                if (current.items.any { !it.keyUsable && (it.enabled || !it.keyFailed) }) {
                    val items = current.items.map {
                        if (it.keyUsable) it else it.copy(enabled = false, keyFailed = true)
                    }
                    val next = pairingDocument(document, current, items, current.listed)
                    persist(next, alias, LoginAttempt())
                    pairings(next, authority)
                } else {
                    current
                }
            }
        }
    }

    override suspend fun reconcile(expected: PairingSnapshot, items: List<RemotePairing>): Boolean =
        updatePairings(expected, listed = true) { current ->
            items.map { remote ->
                val local = (current.items + current.provisional).find {
                    it.pairingId == remote.pairingId
                }
                require(local == null || local.receiverId == remote.receiverId)
                local?.copy(alias = remote.alias, provisionalSessionId = null)
                    ?: OwnPairing(remote.pairingId, remote.receiverId, remote.alias)
            }
        }

    override suspend fun select(
        expected: PairingSnapshot,
        pairingId: String,
        enabled: Boolean
    ): Boolean = updatePairings(expected, listed = expected.listed) { current ->
        current.items.map {
            if (it.pairingId == pairingId) it.copy(enabled = enabled && it.keyUsable) else it
        }
    }

    override suspend fun saveProvisional(expected: PairingSnapshot, pairing: OwnPairing): Boolean =
        updatePairings(expected, expected.listed) { current ->
            require(pairing.provisionalSessionId != null && pairing.keyUsable)
            require(
                (current.items + current.provisional).none { it.pairingId == pairing.pairingId }
            )
            require(current.items.none { it.receiverId == pairing.receiverId })
            current.items + pairing
        }

    override suspend fun reserveEncryption(expected: PairingSnapshot, pairingId: String): Boolean =
        updatePairings(expected, expected.listed, advanceRevision = false) { current ->
            val target = current.items.single { it.pairingId == pairingId }
            require(target.keyUsable && target.encryptions in 2 until 4294967296L)
            current.items.map {
                if (it.pairingId == pairingId) it.copy(encryptions = it.encryptions + 1) else it
            }
        }

    private suspend fun updatePairings(
        expected: PairingSnapshot,
        listed: Boolean,
        advanceRevision: Boolean = true,
        transform: (PairingSnapshot) -> List<OwnPairing>
    ): Boolean = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, authority, alias) = load()
                val current = pairings(document, authority)
                if (authority.generation != expected.authority.generation ||
                    authority.identity == null || authority.refreshState.closesRegistration ||
                    authority.refreshState != expected.authority.refreshState ||
                    authority.identity.accountId != expected.authority.identity?.accountId ||
                    authority.identity.senderId != expected.authority.identity.senderId ||
                    current.revision != expected.revision
                ) {
                    return@storageOperation false
                }
                persist(
                    pairingDocument(document, current, transform(current), listed, advanceRevision),
                    alias,
                    LoginAttempt()
                )
            }
        }
    }

    private fun pairings(document: JsonObject, authority: IdentitySnapshot): PairingSnapshot {
        val items = document["pairings"]?.jsonArray?.map { entry ->
            val fields = entry.jsonObject
            val metadata = json.decodeFromJsonElement<OwnPairing>(
                JsonObject(fields + ("key" to JsonNull))
            )
            val key = try {
                fields["key"]?.let { json.decodeFromJsonElement<ByteArray?>(it) }
            } catch (_: IllegalArgumentException) {
                null
            }
            metadata.copy(key = key)
        }.orEmpty()
        if (items.isNotEmpty()) {
            require(
                document["pairingsAccountId"]?.jsonPrimitive?.content ==
                    authority.identity?.accountId
            )
            require(
                document["pairingsSenderId"]?.jsonPrimitive?.content == authority.identity?.senderId
            )
            require(items.map { it.pairingId }.distinct().size == items.size)
        }
        return PairingSnapshot(
            authority,
            document["pairingsRevision"]?.jsonPrimitive?.long ?: 0,
            document["pairingsListed"]?.jsonPrimitive?.boolean ?: false,
            items.filter { it.provisionalSessionId == null },
            items.filter { it.provisionalSessionId != null }
        )
    }

    private fun pairingDocument(
        document: JsonObject,
        current: PairingSnapshot,
        items: List<OwnPairing>,
        listed: Boolean,
        advanceRevision: Boolean = true
    ): JsonObject {
        val originals = document["pairings"]?.jsonArray?.associateBy {
            it.jsonObject.getValue("pairingId").jsonPrimitive.content
        }.orEmpty()
        return JsonObject(
            document + mapOf(
                "pairingsAccountId" to JsonPrimitive(current.authority.identity?.accountId),
                "pairingsSenderId" to JsonPrimitive(current.authority.identity?.senderId),
                "pairingsRevision" to
                    JsonPrimitive(Math.addExact(current.revision, if (advanceRevision) 1 else 0)),
                "pairingsListed" to JsonPrimitive(listed),
                "pairings" to JsonArray(
                    (
                        items + current.provisional.filter { pending ->
                            items.none { it.pairingId == pending.pairingId }
                        }
                        ).map {
                        JsonObject(
                            originals[it.pairingId]?.jsonObject.orEmpty() +
                                json.encodeToJsonElement(it).jsonObject.let { fields ->
                                    if (it.key == null) fields - "key" else fields
                                }
                        )
                    }
                )
            )
        )
    }

    override suspend fun read(): IdentitySnapshot = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation { load().second }
        }
    }

    override suspend fun logout(): RefreshCredential? = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, original, alias) = load(applyLogout = false)
                if (original.refreshState.closesRegistration) return@storageOperation null
                FileOutputStream(logoutMarker).use { stream ->
                    stream.write(1)
                    stream.fd.sync()
                }
                val next = original.loggedOut()
                check(persist(json.logoutDocument(document, next), alias, LoginAttempt()))
                original.identity?.refresh
            }
        }
    }

    override suspend fun updateRenewal(
        expected: IdentitySnapshot,
        replacement: IdentitySnapshot,
        attempt: LoginAttempt
    ): Boolean = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, original, alias) = load()
                if (original.generation != expected.generation ||
                    original.identity?.accountId != expected.identity?.accountId ||
                    original.identity?.senderId != expected.identity?.senderId ||
                    original.renewal != expected.renewal ||
                    original.refreshState != expected.refreshState ||
                    original.closedSenderIds != expected.closedSenderIds
                ) {
                    return@storageOperation false
                }
                if (replacement.refreshState.closesRegistration || original.identity == null) {
                    validateRegistrationTransition(original, replacement)
                    return@storageOperation persist(
                        registrationDocument(replacement),
                        alias,
                        attempt
                    )
                }
                check(!original.refreshState.closesRegistration)
                require(replacement.closedSenderIds == original.closedSenderIds)
                val identity = checkNotNull(replacement.identity)
                val originalIdentity = checkNotNull(original.identity)
                require(
                    identity.accountId == originalIdentity.accountId &&
                        identity.senderId == originalIdentity.senderId
                )
                val replacingBundle = identity !== expected.identity
                val nextGeneration = Math.addExact(
                    original.generation,
                    if (replacingBundle) 1L else 0L
                )
                require(replacement.generation == nextGeneration)
                identity.validate(Instant.now(), requireFresh = replacingBundle)
                if (replacingBundle) {
                    require(
                        original.renewal != null && replacement.renewal == null &&
                            replacement.refreshState == RefreshState.Active
                    )
                }
                replacement.renewal?.validate(identity, replacement.generation)
                val next = JsonObject(
                    document + mapOf(
                        "generation" to JsonPrimitive(replacement.generation),
                        "identity" to json.encodeToJsonElement(identity),
                        "renewal" to json.encodeToJsonElement(replacement.renewal),
                        "refreshState" to json.encodeToJsonElement(replacement.refreshState)
                    )
                )
                persist(
                    next,
                    alias,
                    attempt,
                    registrationRevoked =
                        replacement.refreshState == RefreshState.RegistrationRevoked
                )
            }
        }
    }

    override suspend fun accept(
        expectedGeneration: Long,
        identity: SenderIdentity,
        attempt: LoginAttempt
    ): Boolean = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, original, alias) = load()
                if (original.generation != expectedGeneration) return@storageOperation false
                check(!original.refreshState.closesRegistration)
                check(identity.senderId !in original.closedSenderIds)
                val previous = original.identity
                if (previous == null) {
                    check(
                        original.refreshState == RefreshState.Active ||
                            original.refreshState == RefreshState.LoggedOut
                    )
                } else {
                    require(
                        identity.accountId == previous.accountId &&
                            identity.senderId == previous.senderId
                    )
                }
                identity.validate(Instant.now(), requireFresh = true)
                val retained = original.restoration
                val sameSender = retained == null ||
                    retained == SenderContext(identity.accountId, identity.senderId)
                val base = if (sameSender) {
                    document
                } else {
                    JsonObject(
                        document.filterKeys {
                            !it.startsWith("pairings")
                        }
                    )
                }
                val next = JsonObject(
                    base + mapOf(
                        "version" to JsonPrimitive(1),
                        "generation" to JsonPrimitive(Math.addExact(original.generation, 1)),
                        "identity" to json.encodeToJsonElement(identity),
                        "renewal" to JsonNull,
                        "restoration" to JsonNull,
                        "refreshState" to json.encodeToJsonElement(RefreshState.Active)
                    )
                )
                val accepted = persist(next, alias, attempt)
                if (accepted && logoutMarker.exists()) check(logoutMarker.delete())
                accepted
            }
        }
    }

    private fun load(applyLogout: Boolean = true): Triple<JsonObject, IdentitySnapshot, String?> {
        if (!file.exists()) return Triple(JsonObject(emptyMap()), IdentitySnapshot(), null)
        val envelope = json.decodeFromString<ProtectedSnapshot>(file.readText(Charsets.UTF_8))
        val plaintext = protection.open(envelope)
        val document = try {
            json.decodeFromString<JsonObject>(plaintext.toString(Charsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
        require(document.getValue("version").jsonPrimitive.int == 1)
        val generation = document.getValue("generation").jsonPrimitive.long
        require(generation > 0)
        val identity = document.getValue("identity").let {
            if (it == JsonNull) null else json.decodeFromJsonElement<SenderIdentity>(it)
        }
        identity?.validate(Instant.now(), requireFresh = false)
        val renewal = document["renewal"]?.let { json.decodeFromJsonElement<RenewalOperation?>(it) }
        val refreshState = document["refreshState"]?.let {
            json.decodeFromJsonElement<RefreshState>(it)
        } ?: RefreshState.Active
        val closedSenderIds = document["closedSenderIds"]?.let {
            json.decodeFromJsonElement<Set<String>>(it)
        }.orEmpty()
        require(closedSenderIds.none { it.isBlank() })
        renewal?.validate(checkNotNull(identity), generation)
        val restoration = json.readRestoration(document)
        val snapshot =
            IdentitySnapshot(
                generation,
                identity,
                renewal,
                refreshState,
                closedSenderIds,
                restoration
            )
        if (logoutApplies(snapshot, applyLogout)) {
            val loggedOut = snapshot.loggedOut()
            return Triple(json.logoutDocument(document, loggedOut), loggedOut, envelope.keyAlias)
        }
        if (refreshState.closesRegistration && identity != null) {
            val closed = snapshot.copy(
                generation = Math.addExact(generation, 1),
                identity = null,
                renewal = null,
                closedSenderIds = closedSenderIds + identity.senderId
            )
            val migrated = registrationDocument(closed)
            check(persist(migrated, envelope.keyAlias, LoginAttempt()))
            return Triple(migrated, closed, envelope.keyAlias)
        }
        require(identity == null || identity.senderId !in closedSenderIds)
        require(!refreshState.closesRegistration || closedSenderIds.isNotEmpty())
        return Triple(
            document,
            snapshot,
            envelope.keyAlias
        )
    }

    private fun validateRegistrationTransition(
        original: IdentitySnapshot,
        replacement: IdentitySnapshot
    ) {
        require(replacement.generation == Math.addExact(original.generation, 1))
        require(replacement.identity == null && replacement.renewal == null)
        if (original.identity != null) {
            require(replacement.refreshState.closesRegistration)
            require(
                replacement.closedSenderIds ==
                    original.closedSenderIds + original.identity.senderId
            )
        } else {
            require(original.refreshState.closesRegistration)
            require(
                replacement.refreshState == RefreshState.Active ||
                    (
                        original.refreshState == RefreshState.RegistrationRevoked &&
                            replacement.refreshState == RefreshState.RegistrationClosed
                        )
            )
            require(replacement.closedSenderIds == original.closedSenderIds)
        }
    }

    private fun logoutApplies(snapshot: IdentitySnapshot, enabled: Boolean): Boolean =
        enabled && logoutMarker.exists() && !snapshot.refreshState.closesRegistration

    private fun registrationDocument(snapshot: IdentitySnapshot) = JsonObject(
        mapOf(
            "version" to JsonPrimitive(1),
            "generation" to JsonPrimitive(snapshot.generation),
            "identity" to JsonNull,
            "renewal" to JsonNull,
            "refreshState" to json.encodeToJsonElement(snapshot.refreshState),
            "closedSenderIds" to json.encodeToJsonElement(snapshot.closedSenderIds)
        )
    )

    private fun persist(
        document: JsonObject,
        alias: String?,
        attempt: LoginAttempt,
        registrationRevoked: Boolean = false
    ): Boolean {
        val plaintext = json.encodeToString(document).toByteArray(Charsets.UTF_8)
        val protected = try {
            protection.seal(plaintext, alias)
        } finally {
            plaintext.fill(0)
        }
        return write(
            json.encodeToString(protected).toByteArray(Charsets.UTF_8),
            attempt,
            registrationRevoked
        )
    }

    private fun write(
        bytes: ByteArray,
        attempt: LoginAttempt,
        registrationRevoked: Boolean
    ): Boolean {
        val pending = File(file.parentFile, "identity.snapshot.new")
        FileOutputStream(pending).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        return attempt.commit(registrationRevoked) {
            Files.move(
                pending.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private inline fun <Result> storageOperation(action: () -> Result): Result = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        throw IdentityStorageException()
    }
}
