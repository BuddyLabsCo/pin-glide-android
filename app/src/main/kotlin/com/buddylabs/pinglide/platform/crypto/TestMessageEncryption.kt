package com.buddylabs.pinglide.platform.crypto

import com.buddylabs.pinglide.domain.forwarding.TestCiphertext
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun encryptTestMessage(key: ByteArray): TestCiphertext {
    val plaintext = JsonObject(
        mapOf(
            "type" to JsonPrimitive("verification_message"),
            "smsText" to JsonPrimitive("【鹊信】这是一条测试短信，验证码 482913。请勿用于真实登录。"),
            "otp" to JsonPrimitive("482913")
        )
    ).toString().toByteArray(Charsets.UTF_8)
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
