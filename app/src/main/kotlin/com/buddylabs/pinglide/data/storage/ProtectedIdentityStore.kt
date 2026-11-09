package com.buddylabs.pinglide.data.storage

import android.content.Context
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.RenewalOperation
import com.buddylabs.pinglide.domain.identity.SenderIdentity
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ProtectedIdentityStore(context: Context) : IdentityStore {
    private val file = File(context.noBackupFilesDir, "identity.snapshot")
    private val protection = SnapshotProtection()
    private val writer = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

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
