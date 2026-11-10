package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.forwarding.TestCiphertext
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.generated.http.api.DeliveryApi
import com.buddylabs.pinglide.generated.http.infrastructure.ApiResponse
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.ServerError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.UploadItem
import com.buddylabs.pinglide.generated.http.model.UploadRequest
import com.buddylabs.pinglide.generated.http.model.UploadResponse
import com.buddylabs.pinglide.generated.http.model.UploadResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient

class TestDeliveryService(private val baseUrl: String, client: OkHttpClient) :
    com.buddylabs.pinglide.domain.forwarding.TestDeliveryService {
    private val client = client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()

    override suspend fun send(
        token: String,
        messageId: String,
        target: OwnPairing,
        encrypted: TestCiphertext
    ): TestMessageResult {
        val request = UploadRequest(
            listOf(
                UploadItem(
                    encrypted.ciphertext,
                    1,
                    encrypted.nonce,
                    target.pairingId,
                    target.receiverId
                )
            ),
            messageId
        )
        val received = AtomicBoolean(false)
        val observed = client.newBuilder().addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).also { received.set(true) }
        }.build()
        val api = DeliveryApi(baseUrl, identityCalls(observed)).apply {
            accessTokenProvider =
                { token }
        }
        repeat(2) { attempt ->
            received.set(false)
            val response = try {
                api.postMessagesWithHttpInfo(request)
            } catch (_: IOException) {
                if (attempt == 0 && !received.get()) return@repeat
                return TestMessageResult.Uncertain
            } catch (_: SerializationException) {
                return TestMessageResult.Uncertain
            }
            val code = responseCode(response)
            if (attempt == 0 && response.statusCode == 503 &&
                code == "delivery_status_unavailable"
            ) {
                return@repeat
            }
            return result(response, request)
        }
        return TestMessageResult.Uncertain
    }

    private fun responseCode(response: ApiResponse<UploadResponse?>): String? {
        val body = when (response) {
            is ClientError -> response.body
            is ServerError -> response.body
            else -> null
        }
        requireCurrentSenderAccess(response.statusCode, body)
        val code = serviceProblem(response.statusCode, body)?.code
        if (response.statusCode == 401 &&
            code in setOf("credential_invalid", "credential_revoked")
        ) {
            throw PairingServiceException(PairingFailure.LoginRequired)
        }
        return code
    }

    private fun rejection(code: String?): TestMessageResult = when (code) {
        "receiver_not_authorized" -> TestMessageResult.InvalidPairing
        "invalid_delivery_item" -> TestMessageResult.InvalidPayload
        "unsupported_crypto_version" -> TestMessageResult.UnsupportedEncryption
        "duplicate_receiver" -> TestMessageResult.DuplicateTarget
        else -> TestMessageResult.Uncertain
    }

    private fun result(
        response: ApiResponse<UploadResponse?>,
        request: UploadRequest
    ): TestMessageResult {
        if (response !is Success || response.statusCode != 207) return TestMessageResult.Uncertain
        val result = response.data ?: return TestMessageResult.Uncertain
        val item = result.results.singleOrNull() ?: return TestMessageResult.Uncertain
        if (result.messageId != request.messageId || item.index != 0L ||
            item.receiverId != request.items.single().receiverId
        ) {
            return TestMessageResult.Uncertain
        }
        return when (item.status) {
            UploadResult.Status.received -> if (item.error ==
                null
            ) {
                TestMessageResult.Received
            } else {
                TestMessageResult.Uncertain
            }

            UploadResult.Status.offline -> if (item.error?.code == "offline") {
                TestMessageResult.Offline
            } else {
                TestMessageResult.Uncertain
            }

            UploadResult.Status.rejected -> rejection(item.error?.code)

            else -> TestMessageResult.Uncertain
        }
    }
}
