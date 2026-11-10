package com.buddylabs.pinglide.platform.crypto

import com.buddylabs.pinglide.domain.pairing.InvalidPairingQrException
import com.buddylabs.pinglide.domain.pairing.PairingQr
import com.buddylabs.pinglide.domain.pairing.PairingQrDecoder
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.bouncycastle.asn1.sec.SECNamedCurves

class PairingQrReader : PairingQrDecoder {
    override fun decode(text: String): PairingQr {
        try {
            require(text.length <= 4096)
            val fields = Json.parseToJsonElement(text) as? JsonObject
                ?: throw InvalidPairingQrException()
            require(
                fields.keys == setOf(
                    "version",
                    "cryptoVersion",
                    "sessionId",
                    "receiverId",
                    "recipientPublicKey",
                    "psk"
                )
            )
            for (name in listOf("version", "cryptoVersion")) {
                val version = fields[name] as? JsonPrimitive
                require(version != null && !version.isString && version.intOrNull == 1)
            }
            fun value(name: String): String {
                val item = fields[name] as? JsonPrimitive
                require(item != null && item.isString)
                return item.content
            }
            val sessionId = value("sessionId")
            val receiverId = value("receiverId")
            require(identifier.matches(sessionId) && identifier.matches(receiverId))
            val key = decodeBytes(value("recipientPublicKey"), 65)
            require(key[0] == 4.toByte())
            val point = SECNamedCurves.getByName("secp256r1").curve.decodePoint(key)
            require(!point.isInfinity && point.isValid)
            return PairingQr(sessionId, receiverId, key, decodeBytes(value("psk"), 32))
        } catch (_: IllegalArgumentException) {
            throw InvalidPairingQrException()
        } catch (_: SerializationException) {
            throw InvalidPairingQrException()
        }
    }

    private fun decodeBytes(text: String, size: Int): ByteArray {
        val bytes = Base64.getDecoder().decode(text)
        require(bytes.size == size && Base64.getEncoder().encodeToString(bytes) == text)
        return bytes
    }

    private val identifier = Regex("[A-Za-z0-9_-]{1,128}")
}
