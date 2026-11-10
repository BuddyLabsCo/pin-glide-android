package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingEntryException
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEvent
import com.buddylabs.pinglide.domain.pairing.PairingSession
import com.buddylabs.pinglide.generated.wss.PairingState
import com.buddylabs.pinglide.generated.wss.WssError
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

internal fun pairingSessionEvents(
    baseUrl: String,
    client: OkHttpClient,
    token: String,
    sessionId: String
) = flow<PairingSession> {
    PairingChannel(baseUrl, client, token, sessionId).use { connection ->
        connection.events.collect { event ->
            if (event is PairingEvent.State) emit(event.session)
        }
    }
}

private val sessionJson = Json { ignoreUnknownKeys = true }

internal fun decodeSessionEvent(text: String, expectedId: String): PairingSession? {
    try {
        require(text.length <= 65536)
        val envelope = sessionJson.parseToJsonElement(text).jsonObject
        val version = envelope["version"]?.jsonPrimitive
        require(version != null && !version.isString && version.intOrNull == 1)
        return when (envelope["type"]?.jsonPrimitive?.content) {
            "pairing_state" -> {
                val event = sessionJson.decodeFromString<PairingState>(text)
                val item = event.session
                require(item.sessionId == expectedId)
                sessionView(
                    item.sessionId, item.receiverId, item.senderId, item.pairingId,
                    item.mode.value, item.state.value, item.expiresAt,
                    item.senderConfirmed, item.receiverConfirmed
                )
            }

            "error" -> {
                val event = sessionJson.decodeFromString<WssError>(text)
                require(event.sessionId == null || event.sessionId == expectedId)
                throw PairingEntryException(sessionProblem(event.error.code))
            }

            else -> throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
    } catch (_: SerializationException) {
        throw PairingEntryException(PairingEntryProblem.Unavailable)
    } catch (_: IllegalArgumentException) {
        throw PairingEntryException(PairingEntryProblem.Unavailable)
    }
}
