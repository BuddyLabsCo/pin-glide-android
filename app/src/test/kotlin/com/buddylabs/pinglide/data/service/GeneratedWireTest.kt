package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.generated.http.model.UploadResponse
import com.buddylabs.pinglide.generated.http.model.UploadResult
import com.buddylabs.pinglide.generated.wss.BusinessError
import com.buddylabs.pinglide.generated.wss.PairingRelayIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GeneratedWireTest {
    private val serviceJson = Json { ignoreUnknownKeys = true }

    @Test
    fun mixedUploadResultPreservesIndexesAndUnknownStatusIsNotSuccess() {
        val response = serviceJson.decodeFromString<UploadResponse>(
            """{"messageId":"message","requestId":"request","results":[
              {"index":0,"status":"received","receiverId":"one"},
              {"index":1,"status":"future_status","error":{"code":"future_error","detail":"example"}}
            ]}"""
        )
        assertEquals(listOf(0L, 1L), response.results.map { it.index })
        assertEquals(UploadResult.Status.received, response.results[0].status)
        assertEquals(UploadResult.Status.unknown_default_open_api, response.results[1].status)
        assertEquals("future_error", response.results[1].error?.code)
        assertEquals(
            response,
            serviceJson.decodeFromString<UploadResponse>(serviceJson.encodeToString(response))
        )
    }

    @Test
    fun unknownOptionalFieldsAndFutureErrorCodesSurviveServiceDecoding() {
        val error = serviceJson.decodeFromString<BusinessError>(
            """{"code":"future_service_error","detail":"example","future":true}"""
        )
        assertEquals("future_service_error", error.code)
        assertNull(error.errors)
        assertEquals(
            error,
            serviceJson.decodeFromString<BusinessError>(serviceJson.encodeToString(error))
        )
    }

    @Test
    fun requiredRelayFieldsCannotBeMissingOrNull() {
        for (payload in listOf(
            """{"version":1,"type":"pairing_relay","sessionId":"example"}""",
            """{"version":1,"type":"pairing_relay","sessionId":"example","payload":null}"""
        )) {
            assertThrows(SerializationException::class.java) {
                serviceJson.decodeFromString<PairingRelayIn>(payload)
            }
        }
    }

    @Test
    fun relayRoundTripRetainsOriginalWireNames() {
        val relay =
            PairingRelayIn(
                payload = "example",
                sessionId = "session",
                type = "pairing_relay",
                version = 1
            )
        val wire = serviceJson.encodeToString(relay)
        assertEquals(relay, serviceJson.decodeFromString<PairingRelayIn>(wire))
        assertEquals(
            "session",
            serviceJson.parseToJsonElement(
                wire
            ).jsonObject.getValue("sessionId").jsonPrimitive.content
        )
    }
}
