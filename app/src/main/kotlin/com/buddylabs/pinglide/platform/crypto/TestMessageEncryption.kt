package com.buddylabs.pinglide.platform.crypto

import com.buddylabs.pinglide.domain.forwarding.TestCiphertext
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import java.security.GeneralSecurityException
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Cipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private class VerificationMessage(
    val type: String,
    val smsText: String,
    val otp: String,
    val receivedAt: String? = null
)

fun encryptTestMessage(key: ByteArray, receivedAt: Long): TestCiphertext = encryptMessage(
    key,
    "【鹊信】这是一条测试短信，验证码 482913。请勿用于真实登录。",
    "482913",
    receivedAt
)

fun encryptMessage(
    key: ByteArray,
    smsText: String,
    otp: String,
    receivedAt: Long?
): TestCiphertext {
    val plaintext = Json.encodeToString(
        VerificationMessage(
            "verification_message",
            smsText,
            otp,
            receivedAt?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
                ?.takeIf { it.atOffset(ZoneOffset.UTC).year <= 9999 }?.toString()
        )
    ).toByteArray(Charsets.UTF_8)
    val nonce = randomBytes(12)
    val ciphertext = try {
        pairingCipher(Cipher.ENCRYPT_MODE, key, nonce, plaintext)
    } catch (_: GeneralSecurityException) {
        throw IdentityStorageException()
    } finally {
        plaintext.fill(0)
    }
    return TestCiphertext(
        Base64.getEncoder().encodeToString(nonce),
        Base64.getEncoder().encodeToString(ciphertext)
    )
}
