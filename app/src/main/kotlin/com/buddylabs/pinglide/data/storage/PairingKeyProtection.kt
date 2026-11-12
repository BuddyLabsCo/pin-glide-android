package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.platform.crypto.ProtectedSnapshot
import com.buddylabs.pinglide.platform.crypto.SnapshotProtection
import java.security.GeneralSecurityException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun Json.readPairing(
    document: JsonObject,
    fields: JsonObject,
    protection: SnapshotProtection
): OwnPairing {
    val metadata = pairingMetadata(fields)
    val key = try {
        val reference = fields["protectedKey"]
        if (reference == null || reference == JsonNull) {
            decodePairingKey(fields["key"] ?: JsonNull)
        } else {
            val envelope = decodeFromJsonElement<ProtectedSnapshot>(reference)
            require(envelope.version == 2)
            val plaintext = protection.open(envelope, pairingKeyContext(document, metadata))
            try {
                decodePairingKey(parseToJsonElement(plaintext.toString(Charsets.UTF_8)))
            } finally {
                plaintext.fill(0)
            }
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
    return metadata.copy(key = key)
}

internal fun Json.protectPairingKeys(
    document: JsonObject,
    protection: SnapshotProtection
): JsonObject {
    snapshotAuthority(document)
    val items = document["pairings"]?.jsonArray.orEmpty().map { entry ->
        val fields = entry.jsonObject
        val metadata = pairingMetadata(fields)
        val key = fields["key"]
        val reference = if (key != null && key != JsonNull) {
            val existing = fields["protectedKey"]?.takeUnless { it == JsonNull }?.let {
                decodeFromJsonElement<ProtectedSnapshot>(it).keyAlias
            }
            val plaintext = encodeToString(key).toByteArray(Charsets.UTF_8)
            try {
                encodeToJsonElement(
                    protection.seal(plaintext, existing, pairingKeyContext(document, metadata))
                )
            } finally {
                plaintext.fill(0)
            }
        } else {
            fields["protectedKey"] ?: JsonNull
        }
        val protected = JsonObject(fields - "key" + ("protectedKey" to reference))
        val resolved = readPairing(document, protected, protection)
        JsonObject(
            protected + mapOf(
                "enabled" to JsonPrimitive(metadata.enabled && resolved.keyUsable),
                "keyFailed" to JsonPrimitive(metadata.keyFailed || !resolved.keyUsable)
            )
        )
    }
    return JsonObject(
        document + mapOf(
            "version" to JsonPrimitive(IDENTITY_SNAPSHOT_VERSION),
            "pairings" to JsonArray(items)
        )
    )
}

internal fun Json.validateKeyReferences(document: JsonObject) {
    for (entry in document["pairings"]?.jsonArray.orEmpty()) {
        val fields = entry.jsonObject
        require("key" !in fields)
        val reference = fields["protectedKey"] as? JsonObject ?: continue
        val version = reference["version"]?.jsonPrimitive?.int ?: continue
        require(version <= 2)
    }
}

private fun Json.decodePairingKey(value: JsonElement): ByteArray? {
    if (value == JsonNull) return null
    require(value.jsonArray.size == 32)
    require(value.jsonArray.all { !it.jsonPrimitive.isString && it.jsonPrimitive.int in -128..127 })
    return decodeFromJsonElement<ByteArray>(value)
}

private fun pairingKeyContext(document: JsonObject, pairing: OwnPairing): String = JsonArray(
    listOf(
        JsonPrimitive("pairing-key"),
        document.getValue("pairingsAccountId"),
        document.getValue("pairingsSenderId"),
        JsonPrimitive(pairing.pairingId),
        JsonPrimitive(pairing.receiverId)
    )
).toString()
