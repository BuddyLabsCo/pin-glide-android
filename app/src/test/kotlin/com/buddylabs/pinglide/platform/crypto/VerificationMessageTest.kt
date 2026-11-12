package com.buddylabs.pinglide.platform.crypto

import com.buddylabs.pinglide.domain.forwarding.TestCiphertext
import java.util.Base64
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VerificationMessageTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun encryptedSmsPreservesOriginalTimestampWithAndWithoutOtp() {
        for (otp in listOf("482913", "")) {
            val payload = decrypt(encryptMessage(key, "Code 482913", otp, 1_794_492_009_123L))

            assertEquals("verification_message", payload.getValue("type").jsonPrimitive.content)
            assertEquals("Code 482913", payload.getValue("smsText").jsonPrimitive.content)
            assertEquals(otp, payload.getValue("otp").jsonPrimitive.content)
            assertEquals(
                "2026-11-12T14:00:09.123Z",
                payload.getValue("receivedAt").jsonPrimitive.content
            )
        }
    }

    @Test
    fun unusableTimestampsOmitTheFieldWithoutLosingTheSms() {
        for (timestamp in listOf(
            null,
            0L,
            -1L,
            Long.MIN_VALUE,
            253_402_300_800_000L,
            Long.MAX_VALUE
        )) {
            for (otp in listOf("482913", "")) {
                val payload = decrypt(encryptMessage(key, "Code 482913", otp, timestamp))

                assertFalse(payload.containsKey("receivedAt"))
                assertEquals("Code 482913", payload.getValue("smsText").jsonPrimitive.content)
                assertEquals(otp, payload.getValue("otp").jsonPrimitive.content)
            }
        }
    }

    @Test
    fun oldAndFutureTimestampsKeepTheirPrecisionAcrossTimezones() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Asia/Shanghai", "America/Los_Angeles", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for ((timestamp, expected) in listOf(
                    1L to "1970-01-01T00:00:00.001Z",
                    1_000L to "1970-01-01T00:00:01Z",
                    1_794_492_009_120L to "2026-11-12T14:00:09.120Z",
                    253_402_300_799_999L to "9999-12-31T23:59:59.999Z"
                )) {
                    val payload = decrypt(encryptMessage(key, "Message", "", timestamp))

                    assertEquals(expected, payload.getValue("receivedAt").jsonPrimitive.content)
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun fixedTestMessageUsesTheSuppliedCreationTime() {
        val payload = decrypt(encryptTestMessage(key, 1_794_492_009_123L))

        assertEquals("verification_message", payload.getValue("type").jsonPrimitive.content)
        assertEquals("482913", payload.getValue("otp").jsonPrimitive.content)
        assertEquals(
            "【鹊信】这是一条测试短信，验证码 482913。请勿用于真实登录。",
            payload.getValue("smsText").jsonPrimitive.content
        )
        assertEquals(
            "2026-11-12T14:00:09.123Z",
            payload.getValue("receivedAt").jsonPrimitive.content
        )
    }

    private fun decrypt(encrypted: TestCiphertext): JsonObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, Base64.getDecoder().decode(encrypted.nonce))
        )
        return Json.parseToJsonElement(
            cipher.doFinal(
                Base64.getDecoder().decode(encrypted.ciphertext)
            ).toString(Charsets.UTF_8)
        ).jsonObject
    }
}
