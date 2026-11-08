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

class SnapshotProtection {
    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun seal(plaintext: ByteArray, existingAlias: String?): ProtectedSnapshot {
        val alias = existingAlias ?: createKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        cipher.updateAAD(aad(alias))
        return ProtectedSnapshot(1, alias, cipher.iv, cipher.doFinal(plaintext))
    }

    fun open(snapshot: ProtectedSnapshot): ByteArray {
        require(snapshot.version == 1 && snapshot.nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(snapshot.keyAlias),
            GCMParameterSpec(128, snapshot.nonce)
        )
        cipher.updateAAD(aad(snapshot.keyAlias))
        return cipher.doFinal(snapshot.ciphertext)
    }

    private fun key(alias: String): SecretKey {
        require(alias.startsWith("pinglide.snapshot."))
        return checkNotNull(keyStore.getKey(alias, null) as? SecretKey)
    }

    private fun aad(alias: String) = "PinGlide:snapshot:1:$alias".toByteArray(Charsets.UTF_8)

    private fun createKey(): String {
        val alias = "pinglide.snapshot.${UUID.randomUUID()}"
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
