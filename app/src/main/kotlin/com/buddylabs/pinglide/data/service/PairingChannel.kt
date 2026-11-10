package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingConnection
import com.buddylabs.pinglide.domain.pairing.PairingEntryException
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEvent
import com.buddylabs.pinglide.generated.wss.PairingRelayIn
import com.buddylabs.pinglide.generated.wss.PairingRelayOut
import java.util.Base64
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
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

internal class PairingChannel(
    baseUrl: String,
    client: OkHttpClient,
    token: String,
    private val sessionId: String
) : PairingConnection {
    private val queue = Channel<PairingEvent>(16)
    override val events = queue.receiveAsFlow()
    private val json = Json { ignoreUnknownKeys = true }
    private val socket = client.newWebSocket(
        Request.Builder().url("$baseUrl/v1/pairing-sessions/$sessionId/events")
            .header("Authorization", "Bearer $token")
            .header("Sec-WebSocket-Protocol", "pin-glide.v1").build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (response.header("Sec-WebSocket-Protocol") != "pin-glide.v1") {
                    webSocket.close(1002, null)
                    queue.close(PairingEntryException(PairingEntryProblem.Unavailable))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = decode(text)
                    if (!queue.trySend(event).isSuccess) {
                        webSocket.close(1008, null)
                        queue.close(PairingEntryException(PairingEntryProblem.Unavailable))
                    }
                } catch (failure: PairingEntryException) {
                    webSocket.close(1002, null)
                    queue.close(failure)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                webSocket.close(1002, null)
                queue.close(PairingEntryException(PairingEntryProblem.Unavailable))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                queue.close(PairingEntryException(sessionProblem(reason)))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val failure = try {
                    if (response !=
                        null
                    ) {
                        rejectSessionResponse(response.code, response.body.string())
                    }
                    PairingEntryException(PairingEntryProblem.Unavailable)
                } catch (rejected: Exception) {
                    rejected
                } finally {
                    response?.close()
                }
                queue.close(failure)
            }
        }
    )

    override fun send(payload: ByteArray) {
        val relay =
            PairingRelayIn(
                Base64.getEncoder().encodeToString(payload),
                sessionId,
                "pairing_relay",
                1
            )
        if (!socket.send(json.encodeToString(relay))) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
    }

    private fun decode(text: String): PairingEvent {
        try {
            require(text.length <= 65536)
            val envelope = json.parseToJsonElement(text).jsonObject
            val version = envelope.getValue("version").jsonPrimitive
            require(!version.isString && version.intOrNull == 1)
            if (envelope["type"]?.jsonPrimitive?.content != "pairing_relay") {
                return PairingEvent.State(checkNotNull(decodeSessionEvent(text, sessionId)))
            }
            val relay = json.decodeFromString<PairingRelayOut>(text)
            require(relay.sessionId == sessionId && relay.side == "receiver")
            val bytes = Base64.getDecoder().decode(relay.payload)
            require(
                bytes.size <= 8192 && Base64.getEncoder().encodeToString(bytes) == relay.payload
            )
            return PairingEvent.Relay(bytes)
        } catch (_: IllegalArgumentException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: SerializationException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        } catch (_: NoSuchElementException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
    }

    override fun close() {
        socket.cancel()
        queue.cancel()
    }
}
