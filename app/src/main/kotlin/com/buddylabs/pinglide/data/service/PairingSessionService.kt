package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingEntryException
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingSession
import com.buddylabs.pinglide.generated.http.api.PairingApi
import com.buddylabs.pinglide.generated.http.infrastructure.ApiResponse
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.AuthorizeRequest
import com.buddylabs.pinglide.generated.http.model.CompleteRequest
import com.buddylabs.pinglide.generated.http.model.PairingSessionView
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient

class PairingSessionService(private val baseUrl: String, private val client: OkHttpClient) :
    com.buddylabs.pinglide.domain.pairing.PairingSessionService {
    override suspend fun authorize(token: String, sessionId: String): PairingSession {
        val result = request(token, 200) {
            it.postPairingSessionAuthorizeWithHttpInfo(sessionId, AuthorizeRequest())
        } ?: throw PairingEntryException(PairingEntryProblem.Unavailable)
        return result.toSession()
    }

    override suspend fun cancel(token: String, sessionId: String) {
        request(token, 204) { it.deletePairingSessionWithHttpInfo(sessionId) }
    }

    override fun watch(token: String, sessionId: String) =
        pairingSessionEvents(baseUrl, client, token, sessionId)

    override fun connect(
        token: String,
        sessionId: String
    ): com.buddylabs.pinglide.domain.pairing.PairingConnection =
        PairingChannel(baseUrl, client, token, sessionId)

    override suspend fun complete(token: String, sessionId: String): PairingSession {
        val result = request(token, 200) {
            it.postPairingSessionCompleteWithHttpInfo(
                sessionId,
                CompleteRequest(CompleteRequest.KeyConfirmation.confirmed)
            )
        } ?: throw PairingEntryException(PairingEntryProblem.Unavailable)
        return result.toSession()
    }

    private suspend fun <Result> request(
        token: String,
        status: Int,
        action: suspend (PairingApi) -> ApiResponse<Result>
    ): Result? {
        if (baseUrl.isBlank()) throw PairingEntryException(PairingEntryProblem.Unavailable)
        val api = PairingApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { token }
        }
        val response = try {
            action(api)
        } catch (_: IOException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: SerializationException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
        if (response is ClientError) rejectSessionResponse(response.statusCode, response.body)
        if (response !is Success || response.statusCode != status) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
        return response.data
    }
}

internal fun rejectSessionResponse(status: Int, body: Any?): Nothing {
    requireCurrentSenderAccess(status, body)
    val code = serviceProblem(status, body)?.code
    if (status == 401 && code in setOf("credential_invalid", "credential_revoked")) {
        throw com.buddylabs.pinglide.domain.pairing.PairingServiceException(
            com.buddylabs.pinglide.domain.pairing.PairingFailure.LoginRequired
        )
    }
    throw PairingEntryException(sessionProblem(code))
}

internal fun sessionProblem(code: String?): PairingEntryProblem = when (code) {
    "receiver_limit_reached" -> PairingEntryProblem.Capacity

    "pairing_session_expired", "pairing_session_closed", "pairing_session_claimed",
    "operation_superseded", "resource_not_found" -> PairingEntryProblem.Closed

    "fresh_identity_required", "mode_forbids_credential_issue" -> PairingEntryProblem.Unsupported

    else -> PairingEntryProblem.Unavailable
}

internal fun sessionView(
    sessionId: String,
    receiverId: String,
    senderId: String?,
    pairingId: String?,
    mode: String,
    state: String,
    expiresAt: String,
    senderConfirmed: Boolean,
    receiverConfirmed: Boolean
): PairingSession = try {
    PairingSession(
        sessionId,
        receiverId,
        senderId,
        pairingId,
        mode,
        state,
        Instant.parse(expiresAt),
        senderConfirmed,
        receiverConfirmed
    )
} catch (_: DateTimeException) {
    throw PairingEntryException(PairingEntryProblem.Unavailable)
}

private fun PairingSessionView.toSession(): PairingSession = sessionView(
    sessionId, receiverId, senderId, pairingId, mode.value, state.value, expiresAt,
    senderConfirmed, receiverConfirmed
)
