package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RevocationReply
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceSenderRevocationTest {
    private val identity = SenderIdentity(
        "account",
        "sender",
        ManagementCredential("management", "2030-01-01T00:00:00Z"),
        UploadCredential("upload", "2030-01-01T00:00:00Z"),
        RefreshCredential("refresh", "2030-01-01T00:00:00Z")
    )
    private val client = OkHttpClient()

    @Test
    fun exactSelfTargetClosureIsDefinitiveWithoutReplayingProof() = runBlocking {
        var calls = 0
        val service = ServiceSenderRevocation(
            "https://example.invalid",
            Call.Factory { request ->
                calls++
                responseCall(request, 409, "device_registration_revoked", 409)
            }
        )

        assertEquals(
            RevocationReply.CurrentSenderRevoked,
            service.revoke(identity, "sender", "fresh-proof")
        )
        assertEquals(1, calls)
    }

    @Test
    fun otherTargetClosureAndMismatchedProblemsRemainUnconfirmed() = runBlocking {
        val cases = listOf(
            Triple("other-sender", 409, "device_registration_revoked"),
            Triple("sender", 403, "device_registration_revoked"),
            Triple("sender", 409, "unknown_problem")
        )
        for ((target, status, code) in cases) {
            val service = ServiceSenderRevocation(
                "https://example.invalid",
                Call.Factory { request -> responseCall(request, status, code, status) }
            )
            assertEquals(
                RevocationReply.Unconfirmed,
                service.revoke(identity, target, "fresh-proof")
            )
        }
        val mismatched = ServiceSenderRevocation(
            "https://example.invalid",
            Call.Factory { request ->
                responseCall(request, 409, "device_registration_revoked", 403)
            }
        )
        assertEquals(
            RevocationReply.Unconfirmed,
            mismatched.revoke(identity, "sender", "fresh-proof")
        )
    }

    @Test
    fun confirmedSelfRevocationRetainsTheExplicitSuccessOutcome() = runBlocking {
        val service = ServiceSenderRevocation(
            "https://example.invalid",
            Call.Factory { request -> responseCall(request, 204, "", 204) }
        )
        assertEquals(RevocationReply.Revoked, service.revoke(identity, "sender", "fresh-proof"))
    }

    private fun responseCall(
        request: Request,
        status: Int,
        code: String,
        problemStatus: Int
    ): Call = object : Call by client.newCall(request) {
        override fun enqueue(responseCallback: Callback) {
            responseCallback.onResponse(
                this,
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("Response")
                    .body(
                        """{
                                "status":$problemStatus,"code":"$code",
                                "detail":"Closed","requestId":"request",
                                "title":"Closed","type":"/problems/$code"
                            }
                        """.trimIndent().toResponseBody()
                    )
                    .build()
            )
        }
    }
}
