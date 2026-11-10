package com.buddylabs.pinglide.platform.crypto

import com.buddylabs.pinglide.domain.pairing.PairingEntryException
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingHandshake
import com.buddylabs.pinglide.domain.pairing.PairingQr
import java.nio.charset.CharacterCodingException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.hpke.HPKE

class PairingCryptography : com.buddylabs.pinglide.domain.pairing.PairingCryptography {
    override fun start(qr: PairingQr): PairingHandshake = SenderHandshake(qr)
}

private class SenderHandshake(qr: PairingQr) : PairingHandshake {
    private val challenge = randomBytes(32)
    private var confirmed = false
    override val messageKey: ByteArray
    override val init: ByteArray

    init {
        require(qr.psk.size == 32 && qr.publicKey.size == 65 && qr.publicKey[0] == 4.toByte())
        val hpke =
            HPKE(HPKE.mode_psk, HPKE.kem_P256_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256)
        val context = hpke.SetupPSKS(
            hpke.deserializePublicKey(qr.publicKey),
            "pin-glide/pairing/v1".toByteArray(Charsets.US_ASCII),
            qr.psk,
            qr.sessionId.toByteArray(Charsets.UTF_8)
        )
        messageKey = context.export("pin-glide/message-key/v1".toByteArray(Charsets.US_ASCII), 32)
        val plaintext = jsonBytes("type" to "pairing_init", "challengeA" to encode(challenge))
        init = frame(
            "hpke_init",
            "enc" to encode(context.encapsulation),
            "ciphertext" to encode(context.seal(byteArrayOf(), plaintext))
        )
        plaintext.fill(0)
    }

    override fun confirm(payload: ByteArray): ByteArray {
        try {
            require(!confirmed && payload.size <= 8192)
            val envelope = Json.parseToJsonElement(
                payload.decodeToString(throwOnInvalidSequence = true)
            ).jsonObject
            require(envelope.text("kind") == "key_confirmation")
            val version = envelope.getValue("cryptoVersion").jsonPrimitive
            require(!version.isString && version.intOrNull == 1)
            val plaintext = pairingCipher(
                Cipher.DECRYPT_MODE,
                messageKey,
                decode(envelope.text("nonce"), 12),
                decode(envelope.text("ciphertext"))
            )
            val peer = try {
                Json.parseToJsonElement(
                    plaintext.decodeToString(throwOnInvalidSequence = true)
                ).jsonObject
            } finally {
                plaintext.fill(0)
            }
            require(peer.text("type") == "key_confirmation" && peer.text("role") == "receiver")
            require(MessageDigest.isEqual(challenge, decode(peer.text("challengeA"), 32)))
            val receiverChallenge = decode(peer.text("challengeW"), 32)
            val reply = jsonBytes(
                "type" to "key_confirmation",
                "role" to "sender",
                "challengeW" to encode(receiverChallenge)
            )
            val nonce = randomBytes(12)
            val ciphertext = pairingCipher(Cipher.ENCRYPT_MODE, messageKey, nonce, reply)
            reply.fill(0)
            receiverChallenge.fill(0)
            confirmed = true
            return frame(
                "key_confirmation",
                "nonce" to encode(nonce),
                "ciphertext" to encode(ciphertext)
            )
        } catch (_: IllegalArgumentException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: GeneralSecurityException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: SerializationException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: NoSuchElementException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: CharacterCodingException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
    }

    override fun close() {
        messageKey.fill(0)
        challenge.fill(0)
        init.fill(0)
    }
}

internal fun pairingCipher(
    mode: Int,
    key: ByteArray,
    nonce: ByteArray,
    input: ByteArray
): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
    init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    doFinal(input)
}

internal fun randomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)

private fun JsonObject.text(name: String): String {
    val value = getValue(name).jsonPrimitive
    require(value.isString)
    return value.content
}

private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

private fun decode(text: String, size: Int? = null): ByteArray {
    val bytes = Base64.getDecoder().decode(text)
    require(encode(bytes) == text && (size == null || bytes.size == size))
    return bytes
}

private fun jsonBytes(vararg values: Pair<String, String>): ByteArray = JsonObject(
    values.associate {
        it.first to JsonPrimitive(it.second)
    }
).toString().toByteArray(Charsets.UTF_8)

private fun frame(kind: String, vararg values: Pair<String, String>): ByteArray = JsonObject(
    values.associate { it.first to JsonPrimitive(it.second) } +
        mapOf("kind" to JsonPrimitive(kind), "cryptoVersion" to JsonPrimitive(1))
).toString().toByteArray(Charsets.UTF_8)
