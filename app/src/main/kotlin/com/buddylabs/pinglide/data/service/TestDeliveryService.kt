package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.forwarding.DeliveryItem
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
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
    private val client = client.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .cache(null)
        .build()

    override suspend fun send(
        token: String,
        messageId: String,
        selectItems: suspend () -> List<DeliveryItem>
    ): List<TestMessageResult>? {
        val items = selectItems()
        if (items.isEmpty()) return emptyList()
        val request = UploadRequest(
            items.map { item ->
                UploadItem(
                    item.encrypted.ciphertext,
                    1,
                    item.encrypted.nonce,
                    item.target.pairingId,
                    item.target.receiverId
                )
            },
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
            if (attempt > 0 && selectItems() != items) return null
            received.set(false)
            val response = try {
                api.postMessagesWithHttpInfo(request)
            } catch (_: IOException) {
                if (attempt == 0 && !received.get()) return@repeat
                return List(items.size) { TestMessageResult.Uncertain }
            } catch (_: SerializationException) {
                return List(items.size) { TestMessageResult.Uncertain }
            }
            val code = responseCode(response)
            if (attempt == 0 && response.statusCode == 503 &&
                code == "delivery_status_unavailable"
            ) {
                return@repeat
            }
            if (requestError(response.statusCode, code)) {
                return List(items.size) { TestMessageResult.RequestError }
            }
            return result(response, request)
        }
        return List(items.size) { TestMessageResult.Uncertain }
    }

    private fun requestError(status: Int, code: String?): Boolean =
        status in setOf(400, 422) && code in setOf("invalid_request", "validation_failed")

    private fun responseCode(response: ApiResponse<UploadResponse?>): String? {
        val body = when (response) {
            is ClientError -> response.body
            is ServerError -> response.body
            else -> null
        }
        requireCurrentSenderAccess(response.statusCode, body)
        val code = serviceProblem(response.statusCode, body)?.code
        if (response.statusCode == 401 &&
            code in setOf("credential_missing", "credential_invalid", "credential_revoked")
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
        else -> TestMessageResult.Rejected
    }

    private fun result(
        response: ApiResponse<UploadResponse?>,
        request: UploadRequest
    ): List<TestMessageResult> {
        val uncertain = List(request.items.size) { TestMessageResult.Uncertain }
        if (response !is Success || response.statusCode != 207) return uncertain
        val result = response.data ?: return uncertain
        if (result.messageId != request.messageId || result.results.size != request.items.size ||
            result.results.map { it.index }.toSet() !=
            request.items.indices.map { it.toLong() }.toSet()
        ) {
            return uncertain
        }
        val indexed = result.results.associateBy { it.index }
        return request.items.mapIndexed { index, item ->
            val outcome = indexed.getValue(index.toLong())
            if (outcome.receiverId ==
                item.receiverId
            ) {
                status(outcome)
            } else {
                TestMessageResult.Uncertain
            }
        }
    }

    private fun status(item: UploadResult): TestMessageResult = when (item.status) {
        UploadResult.Status.received -> if (item.error ==
            null
        ) {
            TestMessageResult.Received
        } else {
            TestMessageResult.Uncertain
        }

        UploadResult.Status.offline -> TestMessageResult.Offline

        UploadResult.Status.rejected -> rejection(item.error?.code)

        UploadResult.Status.send_failed -> TestMessageResult.SendFailed

        UploadResult.Status.ack_timeout -> TestMessageResult.AckTimeout

        UploadResult.Status.interrupted -> TestMessageResult.Interrupted

        UploadResult.Status.unknown_default_open_api -> TestMessageResult.Uncertain
    }
}
