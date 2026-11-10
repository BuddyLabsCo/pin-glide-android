package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingEntryException
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingSession
import com.buddylabs.pinglide.generated.wss.PairingState
import com.buddylabs.pinglide.generated.wss.WssError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

internal fun pairingSessionEvents(
    baseUrl: String,
    client: OkHttpClient,
    token: String,
    sessionId: String
) = callbackFlow<PairingSession> {
    val request = Request.Builder().url("$baseUrl/v1/pairing-sessions/$sessionId/events")
        .header("Authorization", "Bearer $token")
        .header("Sec-WebSocket-Protocol", "pin-glide.v1").build()
    val socket = client.newWebSocket(
        request,
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (response.header("Sec-WebSocket-Protocol") != "pin-glide.v1") {
                    close(PairingEntryException(PairingEntryProblem.Unavailable))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    decodeSessionEvent(text, sessionId)?.let { trySend(it) }
                } catch (failure: PairingEntryException) {
                    close(failure)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val problem = try {
                    if (response != null) {
                        rejectSessionResponse(response.code, response.body.string())
                    }
                    PairingEntryException(PairingEntryProblem.Unavailable)
                } catch (rejected: Exception) {
                    rejected
                } finally {
                    response?.close()
                }
                close(problem)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                webSocket.close(1002, null)
                close(PairingEntryException(PairingEntryProblem.Unavailable))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                close(PairingEntryException(sessionProblem(reason)))
            }
        }
    )
    awaitClose { socket.cancel() }
}

private val sessionJson = Json { ignoreUnknownKeys = true }

private fun decodeSessionEvent(text: String, expectedId: String): PairingSession? {
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
