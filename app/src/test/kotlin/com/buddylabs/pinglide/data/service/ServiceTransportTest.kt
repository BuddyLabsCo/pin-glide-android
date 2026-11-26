package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.pairing.PairingEntryException
import java.net.InetAddress
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServiceTransportTest {
    @Test
    fun developmentUsesHttpAndWebSocket() {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            assertTransport(server, serviceHttpClient(debug = true), "http")
        }
    }

    @Test
    fun bothVariantsUseHttpsAndSecureWebSocket() {
        MockWebServer().use { server ->
            val certificates = configureTls(server)
            for (debug in listOf(true, false)) {
                assertTransport(server, trustedClient(debug, certificates), "https")
            }
        }
    }

    @Test
    fun productionRejectsCleartextHttpAndWebSocketBeforeSending() {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val client = serviceHttpClient(debug = false)
            assertThrows(UnknownServiceException::class.java) {
                client.newCall(
                    Request.Builder().url(loopbackUrl(server, "/")).build()
                ).execute().close()
            }
            PairingChannel(
                loopbackUrl(server, "/").toString().removeSuffix("/"),
                client,
                "fixture",
                "session"
            )
                .use { connection ->
                    assertThrows(PairingEntryException::class.java) {
                        runBlocking { withTimeout(5_000) { connection.events.first() } }
                    }
                }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun productionDoesNotFollowTlsRedirectsToCleartextOrOtherTlsEndpoints() {
        MockWebServer().use { cleartext ->
            cleartext.start(InetAddress.getByName("127.0.0.1"), 0)
            MockWebServer().use { secure ->
                val client = trustedClient(false, configureTls(secure))
                for (destination in listOf(
                    loopbackUrl(cleartext, "/redirect"),
                    loopbackUrl(secure, "/redirect")
                )) {
                    secure.enqueue(
                        MockResponse.Builder().code(302).addHeader("Location", destination).build()
                    )
                    client.newCall(Request.Builder().url(loopbackUrl(secure, "/original")).build())
                        .execute().use { response -> assertEquals(302, response.code) }
                    assertEquals("/original", secure.takeRequest(5, TimeUnit.SECONDS)?.target)
                    assertNull(secure.takeRequest(100, TimeUnit.MILLISECONDS))
                    assertEquals(0, cleartext.requestCount)
                }
            }
        }
    }

    @Test
    fun productionDoesNotTrustUnregisteredTlsCertificates() {
        MockWebServer().use { server ->
            configureTls(server)
            val url = loopbackUrl(server, "/")
            assertThrows(SSLHandshakeException::class.java) {
                serviceHttpClient(debug = false)
                    .newCall(Request.Builder().url(url).build()).execute().close()
            }
            assertEquals(0, server.requestCount)
        }
    }

    private fun assertTransport(server: MockWebServer, client: OkHttpClient, scheme: String) {
        server.enqueue(MockResponse(code = 200, body = "fixture"))
        client.newCall(Request.Builder().url(loopbackUrl(server, "/health")).build())
            .execute().use { response -> assertEquals("fixture", response.body.string()) }
        val http = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals(scheme, http.url.scheme)
        assertEquals("/health", http.target)

        server.enqueue(
            MockResponse.Builder().addHeader("Sec-WebSocket-Protocol", "pin-glide.v1")
                .webSocketUpgrade(object : WebSocketListener() {}).build()
        )
        val origin = loopbackUrl(server, "/").toString().removeSuffix("/")
        PairingChannel(origin, client, "fixture", "session").use {
            val websocket = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(scheme, websocket.url.scheme)
            assertEquals("/v1/pairing-sessions/session/events", websocket.target)
            assertEquals("websocket", websocket.headers["Upgrade"])
            if (scheme ==
                "https"
            ) {
                assertNotNull(websocket.handshake)
            } else {
                assertNull(websocket.handshake)
            }
        }
    }

    private fun configureTls(server: MockWebServer): HandshakeCertificates {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(
            certificate
        ).build()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        return HandshakeCertificates.Builder().addTrustedCertificate(
            certificate.certificate
        ).build()
    }

    private fun trustedClient(debug: Boolean, certificates: HandshakeCertificates) =
        serviceHttpClient(debug).newBuilder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .build()

    private fun loopbackUrl(server: MockWebServer, path: String) =
        server.url(path).newBuilder().host("127.0.0.1").build()
}
