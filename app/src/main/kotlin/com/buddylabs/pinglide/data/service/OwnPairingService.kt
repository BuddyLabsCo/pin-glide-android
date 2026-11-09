package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.generated.http.api.PairingApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.ReceiverAssociation
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.serialization.SerializationException
import okhttp3.Call

class OwnPairingService(private val baseUrl: String, private val client: Call.Factory) :
    PairingService {
    override suspend fun list(managementToken: String): List<RemotePairing> {
        if (baseUrl.isBlank()) throw PairingServiceException(PairingFailure.Unavailable)
        val api = PairingApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { managementToken }
        }
        val response = try {
            api.getSelfReceiversWithHttpInfo()
        } catch (_: IOException) {
            throw PairingServiceException(PairingFailure.Unavailable)
        } catch (_: SerializationException) {
            throw PairingServiceException(PairingFailure.Unavailable)
        }
        if (response is ClientError) rejectPairingResponse(response.statusCode, response.body)
        val items = (response as? Success)?.takeIf { it.statusCode == 200 }?.data?.items
            ?: throw PairingServiceException(PairingFailure.Unavailable)
        return mapPairings(items)
    }
}

private fun rejectPairingResponse(status: Int, body: Any?): Nothing {
    requireUnexpiredAccess(status, body)
    val code = serviceProblem(status, body)?.code
    val unusable = code == "credential_invalid" || code == "credential_revoked"
    throw PairingServiceException(
        when {
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
