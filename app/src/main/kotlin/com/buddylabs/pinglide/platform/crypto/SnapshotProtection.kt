package com.buddylabs.pinglide.platform.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable

@Serializable
class ProtectedSnapshot(
    val version: Int,
    val keyAlias: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

class SnapshotProtection(private val stableAlias: String? = null) {
    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun seal(
        plaintext: ByteArray,
        existingAlias: String?,
        context: String = ""
    ): ProtectedSnapshot {
        val alias = existingAlias ?: createKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        cipher.updateAAD(aad(2, alias, context))
        val snapshot = ProtectedSnapshot(2, alias, cipher.iv, cipher.doFinal(plaintext))
        val verified = open(snapshot, context)
        try {
            check(plaintext.contentEquals(verified))
        } finally {
            verified.fill(0)
        }
        return snapshot
    }

    fun open(snapshot: ProtectedSnapshot, context: String = ""): ByteArray {
        require(snapshot.version in 1..2 && snapshot.nonce.size == 12)
        require(snapshot.ciphertext.size >= 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(snapshot.keyAlias),
            GCMParameterSpec(128, snapshot.nonce)
        )
        cipher.updateAAD(aad(snapshot.version, snapshot.keyAlias, context))
        return cipher.doFinal(snapshot.ciphertext)
    }

    private fun key(alias: String): SecretKey {
        require(alias.startsWith("pinglide.snapshot."))
        return checkNotNull(keyStore.getKey(alias, null) as? SecretKey)
    }

    private fun aad(version: Int, alias: String, context: String) = if (version == 1) {
        "PinGlide:snapshot:1:$alias"
    } else {
        "PinGlide:snapshot:2:$alias:$context"
    }.toByteArray(Charsets.UTF_8)

    private fun createKey(): String {
        val alias = stableAlias ?: "pinglide.snapshot.${UUID.randomUUID()}"
        require(alias.startsWith("pinglide.snapshot."))
        if (keyStore.containsAlias(alias)) return alias
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
        return alias
    }
}
