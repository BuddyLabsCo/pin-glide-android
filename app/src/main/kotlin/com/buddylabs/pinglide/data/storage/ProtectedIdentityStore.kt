package com.buddylabs.pinglide.data.storage

import android.content.Context
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.RenewalOperation
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
    PairingStore {
    private val file = File(context.noBackupFilesDir, "identity.snapshot")
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
                val local = current.items.find { it.pairingId == remote.pairingId }
                require(local == null || local.receiverId == remote.receiverId)
                local?.copy(alias = remote.alias)
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

    private suspend fun updatePairings(
        expected: PairingSnapshot,
        listed: Boolean,
        transform: (PairingSnapshot) -> List<OwnPairing>
    ): Boolean = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, authority, alias) = load()
                val current = pairings(document, authority)
                if (authority.generation != expected.authority.generation ||
                    authority.refreshState != expected.authority.refreshState ||
                    authority.identity?.accountId != expected.authority.identity?.accountId ||
                    authority.identity?.senderId != expected.authority.identity?.senderId ||
                    current.revision != expected.revision
                ) {
                    return@storageOperation false
                }
                persist(
                    pairingDocument(document, current, transform(current), listed),
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
            items
        )
    }

    private fun pairingDocument(
        document: JsonObject,
        current: PairingSnapshot,
        items: List<OwnPairing>,
        listed: Boolean
    ): JsonObject {
        val originals = document["pairings"]?.jsonArray?.associateBy {
            it.jsonObject.getValue("pairingId").jsonPrimitive.content
        }.orEmpty()
        return JsonObject(
            document + mapOf(
                "pairingsAccountId" to JsonPrimitive(current.authority.identity?.accountId),
                "pairingsSenderId" to JsonPrimitive(current.authority.identity?.senderId),
                "pairingsRevision" to JsonPrimitive(Math.addExact(current.revision, 1)),
                "pairingsListed" to JsonPrimitive(listed),
                "pairings" to JsonArray(
                    items.map {
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

    override suspend fun updateRenewal(
        expected: IdentitySnapshot,
        replacement: IdentitySnapshot
    ): Boolean = withContext(Dispatchers.IO) {
        writer.withLock {
            storageOperation {
                val (document, original, alias) = load()
                if (original.generation != expected.generation ||
                    original.renewal != expected.renewal ||
                    original.refreshState != expected.refreshState
                ) {
                    return@storageOperation false
                }
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
                persist(next, alias, LoginAttempt())
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
                check(original.generation == expectedGeneration && original.identity == null)
                check(original.generation == 0L)
                identity.validate(Instant.now(), requireFresh = true)
                val next = JsonObject(
                    document + mapOf(
                        "version" to JsonPrimitive(1),
                        "generation" to JsonPrimitive(Math.addExact(original.generation, 1)),
                        "identity" to json.encodeToJsonElement(identity)
                    )
                )
                persist(next, alias, attempt)
            }
        }
    }

    private fun load(): Triple<JsonObject, IdentitySnapshot, String?> {
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
        renewal?.validate(checkNotNull(identity), generation)
        return Triple(
            document,
            IdentitySnapshot(generation, identity, renewal, refreshState),
            envelope.keyAlias
        )
    }

    private fun persist(document: JsonObject, alias: String?, attempt: LoginAttempt): Boolean {
        val plaintext = json.encodeToString(document).toByteArray(Charsets.UTF_8)
        val protected = try {
            protection.seal(plaintext, alias)
        } finally {
            plaintext.fill(0)
        }
        return write(json.encodeToString(protected).toByteArray(Charsets.UTF_8), attempt)
    }

    private fun write(bytes: ByteArray, attempt: LoginAttempt): Boolean {
        val pending = File(file.parentFile, "identity.snapshot.new")
        FileOutputStream(pending).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        return attempt.commit {
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
