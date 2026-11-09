package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.generated.http.api.PairingApi
import com.buddylabs.pinglide.generated.http.infrastructure.ApiResponse
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.PairingAlias
import com.buddylabs.pinglide.generated.http.model.ReceiverAssociation
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.serialization.SerializationException
import okhttp3.Call

class OwnPairingService(private val baseUrl: String, private val client: Call.Factory) :
    PairingService {
    override suspend fun list(managementToken: String): List<RemotePairing> {
        val items = request(managementToken, 200) { it.getSelfReceiversWithHttpInfo() }?.items
            ?: throw PairingServiceException(PairingFailure.Unavailable)
        return mapPairings(items)
    }

    override suspend fun rename(
        managementToken: String,
        pairingId: String,
        alias: String
    ): RemotePairing {
        val item = request(managementToken, 200, allowMissing = true) {
            it.patchSelfPairingWithHttpInfo(pairingId, PairingAlias(alias))
        } ?: throw PairingServiceException(PairingFailure.Unavailable)
        return mapPairings(listOf(item)).single()
    }

    override suspend fun remove(managementToken: String, pairingId: String) {
        request(managementToken, 204, allowMissing = true) {
            it.deleteSelfPairingWithHttpInfo(pairingId)
        }
    }

    private suspend fun <Result> request(
        managementToken: String,
        status: Int,
        allowMissing: Boolean = false,
        action: suspend (PairingApi) -> ApiResponse<Result>
    ): Result? {
        if (baseUrl.isBlank()) throw PairingServiceException(PairingFailure.Unavailable)
        val api = PairingApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { managementToken }
        }
        val response = try {
            action(api)
        } catch (_: IOException) {
            throw PairingServiceException(PairingFailure.Unavailable)
        } catch (_: SerializationException) {
            throw PairingServiceException(PairingFailure.Unavailable)
        }
        if (response is ClientError) {
            rejectPairingResponse(response.statusCode, response.body, allowMissing)
        }
        if (response !is Success || response.statusCode != status) {
            throw PairingServiceException(PairingFailure.Unavailable)
        }
        return response.data
    }
}

private fun rejectPairingResponse(status: Int, body: Any?, allowMissing: Boolean): Nothing {
    requireUnexpiredAccess(status, body)
    val code = serviceProblem(status, body)?.code
    val unusable = code == "credential_invalid" || code == "credential_revoked"
    throw PairingServiceException(
        when {
            allowMissing && status == 404 && code == "resource_not_found" -> PairingFailure.Gone

            status == 409 && code == "device_registration_revoked" ->
                PairingFailure.Revoked

            status == 401 && unusable ->
                PairingFailure.LoginRequired

            else -> PairingFailure.Unavailable
        }
    )
}

private fun mapPairings(items: List<ReceiverAssociation>): List<RemotePairing> {
    try {
        require(items.map { it.pairingId }.distinct().size == items.size)
        return items.map {
            require(it.pairingId.isNotBlank() && it.receiverId.isNotBlank())
            Instant.parse(it.stateChangedAt)
            RemotePairing(
                it.pairingId,
                it.receiverId,
                it.alias,
                when (it.presence) {
                    ReceiverAssociation.Presence.online -> Presence.Online
                    ReceiverAssociation.Presence.offline -> Presence.Offline
                    ReceiverAssociation.Presence.unknown_default_open_api -> Presence.Unknown
                },
                it.stateChangedAt
            )
        }
    } catch (_: IllegalArgumentException) {
        throw PairingServiceException(PairingFailure.Unavailable)
    } catch (_: DateTimeException) {
        throw PairingServiceException(PairingFailure.Unavailable)
    }
}
