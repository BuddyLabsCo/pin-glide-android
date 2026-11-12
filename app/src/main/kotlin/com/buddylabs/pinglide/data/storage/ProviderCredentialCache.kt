package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.generated.http.model.ModelCredential
import com.buddylabs.pinglide.platform.crypto.ProtectedSnapshot
import com.buddylabs.pinglide.platform.crypto.SnapshotProtection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProviderCredentialCache {
    private val protection = SnapshotProtection("pinglide.snapshot.provider")
    private var encrypted: ProtectedSnapshot? = null
    private var alias: String? = null
    private var generation = -1L
    private var session = -1L

    @Volatile
    var revision = 0L
        private set

    @Synchronized
    fun read(generation: Long, session: Long): ModelCredential? {
        if (this.generation != generation || this.session != session) return null
        val snapshot = encrypted ?: return null
        val plaintext = protection.open(snapshot)
        return try {
            Json.decodeFromString<ModelCredential>(plaintext.toString(Charsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun write(
        value: ModelCredential,
        generation: Long,
        session: Long,
        expectedRevision: Long
    ): Boolean {
        if (revision != expectedRevision) return false
        val plaintext = Json.encodeToString(value).toByteArray(Charsets.UTF_8)
        val snapshot = try {
            protection.seal(plaintext, alias)
        } finally {
            plaintext.fill(0)
        }
        encrypted = snapshot
        alias = snapshot.keyAlias
        this.generation = generation
        this.session = session
        return true
    }

    @Synchronized
    fun clear() {
        encrypted?.ciphertext?.fill(0)
        encrypted = null
        revision++
    }
}
