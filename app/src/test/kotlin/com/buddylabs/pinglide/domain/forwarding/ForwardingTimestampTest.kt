package com.buddylabs.pinglide.domain.forwarding

import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RenewalReply
import com.buddylabs.pinglide.domain.identity.RenewalService
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.platform.crypto.encryptMessage
import com.buddylabs.pinglide.platform.crypto.encryptTestMessage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingTimestampTest {
    private val now = Instant.parse("2026-11-12T14:00:09.123Z")

    @Test
    fun delayedExtractionKeepsTheSmsTimeAcrossTargetsAndIdenticalHttpRecovery() = runBlocking {
        withTimeout(5_000) {
            val store = MessageStore(identity())
            val renewal =
                CredentialRenewal(
                    store,
                    RenewalService { _, _ ->
                        error("Unexpected renewal")
                    },
                    this,
                    Clock.fixed(now, ZoneOffset.UTC)
                )
            val extracting = CompletableDeferred<Unit>()
            val extracted = CompletableDeferred<Unit>()
            val requests = mutableListOf<String>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val body = Buffer().also {
                    checkNotNull(chain.request().body).writeTo(it)
                }.readUtf8()
                requests.add(body)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(503).message("Unavailable")
                    .body(
                        """{"code":"delivery_status_unavailable","status":503,
                            "detail":"Unavailable","requestId":"request-64",
                            "title":"Unavailable","type":"/problems/delivery_status_unavailable"}
                        """.toResponseBody(
                            "application/json".toMediaType()
                        )
                    )
                    .build()
            }.build()
            try {
                val forwarding = SmsForwarding(
                    store,
                    renewal,
                    com.buddylabs.pinglide.data.service.TestDeliveryService(
                        "https://delivery.test",
                        client
                    ),
                    ::encryptMessage,
                    { true },
                    OtpExtraction { _, _ ->
                        extracting.complete(Unit)
                        extracted.await()
                        "482913"
                    }
                )
                val operation = async { forwarding.forward(" Code 482913 ", 1_794_492_009_123L) }
                extracting.await()
                assertTrue(requests.isEmpty())
                extracted.complete(Unit)
                operation.await()

                assertEquals(2, requests.size)
                assertEquals(requests[0], requests[1])
                val request = Json.parseToJsonElement(requests[0]).jsonObject
                assertFalse(request.containsKey("receivedAt"))
                val items = request.getValue("items").jsonArray
                assertEquals(2, items.size)
                for ((index, item) in items.withIndex()) {
                    val envelope = item.jsonObject
                    val payload = decrypt(
                        TestCiphertext(
                            envelope.getValue("nonce").jsonPrimitive.content,
                            envelope.getValue("ciphertext").jsonPrimitive.content
                        ),
                        checkNotNull(store.items[index].key)
                    )
                    assertEquals(" Code 482913 ", payload.getValue("smsText").jsonPrimitive.content)
                    assertEquals("482913", payload.getValue("otp").jsonPrimitive.content)
                    assertEquals(
                        "2026-11-12T14:00:09.123Z",
                        payload.getValue("receivedAt").jsonPrimitive.content
                    )
                }
                assertNotEquals(items[0].jsonObject["nonce"], items[1].jsonObject["nonce"])
                assertEquals(listOf(3L, 3L), store.items.map { it.encryptions })
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun missingOrInvalidSmsTimeStillDeliversTheOriginalTextWithEmptyOtp() = runBlocking {
        val store = MessageStore(identity())
        val renewal =
            CredentialRenewal(
                store,
                RenewalService { _, _ ->
                    error("Unexpected renewal")
                },
                this,
                Clock.fixed(now, ZoneOffset.UTC)
            )
        val delivered = mutableListOf<JsonObject>()
        val forwarding = SmsForwarding(
            store,
            renewal,
            TestDeliveryService { _, _, select ->
                delivered.addAll(
                    select().map {
                        decrypt(it.encrypted, checkNotNull(it.target.key))
                    }
                )
                listOf(TestMessageResult.Received, TestMessageResult.Received)
            },
            ::encryptMessage,
            { true },
            OtpExtraction { _, _ -> error("No OTP candidate") }
        )
        for (timestamp in listOf(null, 0L, -1L, Long.MAX_VALUE)) {
            forwarding.forward("Original non-OTP message", timestamp)
        }

        assertEquals(8, delivered.size)
        for (payload in delivered) {
            assertEquals(
                "Original non-OTP message",
                payload.getValue("smsText").jsonPrimitive.content
            )
            assertEquals("", payload.getValue("otp").jsonPrimitive.content)
            assertFalse(payload.containsKey("receivedAt"))
        }
    }

    @Test
    fun testCreationTimePrecedesCredentialWorkAndChangesOnTheNextDeliberateSend() = runBlocking {
        withTimeout(5_000) {
            val clock = MessageClock(now)
            val store = MessageStore(identity(expiry = now.minusSeconds(1)))
            val renewing = CompletableDeferred<Unit>()
            val renewed = CompletableDeferred<Unit>()
            val renewal = CredentialRenewal(
                store,
                RenewalService { _, operation ->
                    renewing.complete(Unit)
                    renewed.await()
                    RenewalReply(
                        operation.operationId,
                        operation.attempt.toLong(),
                        identity("renewed")
                    )
                },
                this,
                clock
            )
            val delivered = mutableListOf<JsonObject>()
            val sender = PairingTestMessage(
                store,
                renewal,
                ::encryptTestMessage,
                TestDeliveryService { _, _, select ->
                    delivered.addAll(
                        select().map {
                            decrypt(it.encrypted, checkNotNull(it.target.key))
                        }
                    )
                    listOf(TestMessageResult.Received)
                },
                clock
            )
            val first = async { sender.send("pairing-0") }
            renewing.await()
            clock.current = now.plusSeconds(120)
            renewed.complete(Unit)
            assertEquals(TestMessageResult.Received, first.await()?.result)
            assertEquals(TestMessageResult.Received, sender.send("pairing-0")?.result)

            assertEquals(
                listOf("2026-11-12T14:00:09.123Z", "2026-11-12T14:02:09.123Z"),
                delivered.map { it.getValue("receivedAt").jsonPrimitive.content }
            )
        }
    }

    private fun identity(token: String = "initial", expiry: Instant = now.plusSeconds(3_600)) =
        SenderIdentity(
            "account",
            "sender",
            ManagementCredential("management-$token", expiry.toString()),
            UploadCredential("upload-$token", expiry.toString()),
            RefreshCredential("refresh-$token", now.plusSeconds(86_400).toString())
        )

    private fun decrypt(encrypted: TestCiphertext, key: ByteArray): JsonObject {
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

    private class MessageClock(var current: Instant) : Clock() {
        override fun instant(): Instant = current

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }

    private class MessageStore(identity: SenderIdentity) :
        IdentityStore,
        PairingStore {
        private var authority = IdentitySnapshot(1, identity)
        var items = List(2) { index ->
            OwnPairing(
                "pairing-$index",
                "receiver-$index",
                "Target $index",
                key = ByteArray(32) {
                    (
                        it +
                            index
                        ).toByte()
                }
            )
        }
            private set

        override suspend fun read(): IdentitySnapshot = authority

        override suspend fun readPairings(): PairingSnapshot =
            PairingSnapshot(authority, items = items)

        override suspend fun updateRenewal(
            expected: IdentitySnapshot,
            replacement: IdentitySnapshot,
            attempt: LoginAttempt
        ): Boolean = authority === expected && attempt.commit { authority = replacement }

        override suspend fun reserveEncryption(
            expected: PairingSnapshot,
            pairingId: String
        ): Boolean {
            check(authority === expected.authority)
            items =
                items.map {
                    if (it.pairingId ==
                        pairingId
                    ) {
                        it.copy(encryptions = it.encryptions + 1)
                    } else {
                        it
                    }
                }
            return true
        }

        override suspend fun accept(
            expectedGeneration: Long,
            identity: SenderIdentity,
            attempt: LoginAttempt
        ): Boolean = error("Unexpected login")

        override suspend fun reconcile(
            expected: PairingSnapshot,
            items: List<RemotePairing>
        ): Boolean = error("Unexpected reconciliation")

        override suspend fun select(
            expected: PairingSnapshot,
            pairingId: String,
            enabled: Boolean
        ): Boolean = error("Unexpected preference change")
    }
}
