package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.RenewalOperation
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal const val IDENTITY_SNAPSHOT_VERSION = 3

internal fun Json.validateSnapshotFields(document: JsonObject) {
    require(
        document.keys.containsAll(
            setOf(
                "version", "generation", "identity", "renewal", "refreshState", "closedSenderIds",
                "restoration", "pairingsRevision", "pairingsListed", "pairings"
            )
        )
    )
    for (entry in document.getValue("pairings").jsonArray) {
        require(
            entry.jsonObject.keys.containsAll(
                setOf(
                    "pairingId",
                    "receiverId",
                    "alias",
                    "enabled",
                    "keyFailed",
                    "provisionalSessionId",
                    "encryptions"
                )
            )
        )
    }
    snapshotAuthority(document)
}

internal fun Json.snapshotAuthority(document: JsonObject): IdentitySnapshot {
    require(document.getValue("version").jsonPrimitive.int in 1..IDENTITY_SNAPSHOT_VERSION)
    val generation = document.getValue("generation").jsonPrimitive.long
    require(generation > 0)
    val identity = decodeFromJsonElement<SenderIdentity?>(document.getValue("identity"))
    identity?.validate(Instant.now(), requireFresh = false)
    val renewal = document["renewal"]?.let { decodeFromJsonElement<RenewalOperation?>(it) }
    renewal?.validate(checkNotNull(identity), generation)
    val state = document["refreshState"]?.let { decodeFromJsonElement<RefreshState>(it) }
        ?: RefreshState.Active
    val closed = document["closedSenderIds"]?.let { decodeFromJsonElement<Set<String>>(it) }
        .orEmpty()
    require(closed.none { it.isBlank() })
    val restoration = readRestoration(document)
    val snapshot = IdentitySnapshot(generation, identity, renewal, state, closed, restoration)
    snapshot.validateState()
    validatePairingOwnership(
        document,
        identity?.accountId ?: restoration?.accountId,
        identity?.senderId ?: restoration?.senderId
    )
    return snapshot
}

private fun IdentitySnapshot.validateState() {
    require(identity == null || restoration == null)
    require(restoration == null || refreshState == RefreshState.LoggedOut)
    require(restoration?.senderId !in closedSenderIds)
    require(refreshState != RefreshState.LoggedOut || (identity == null && renewal == null))
    require(!refreshState.closesRegistration || restoration == null)
    require(refreshState.closesRegistration || identity?.senderId !in closedSenderIds)
    require(!refreshState.closesRegistration || identity != null || closedSenderIds.isNotEmpty())
}

private fun Json.validatePairingOwnership(
    document: JsonObject,
    accountId: String?,
    senderId: String?
) {
    require(document["pairingsRevision"]?.jsonPrimitive?.long?.let { it >= 0 } != false)
    document["pairingsListed"]?.jsonPrimitive?.boolean
    val items = document["pairings"]?.jsonArray.orEmpty().map { pairingMetadata(it.jsonObject) }
    require(items.map { it.pairingId }.distinct().size == items.size)
    val active = items.filter { it.provisionalSessionId == null }
    require(active.map { it.receiverId }.distinct().size == active.size)
    if (items.isNotEmpty()) {
        require(accountId != null && senderId != null)
        require(document["pairingsAccountId"]?.jsonPrimitive?.content == accountId)
        require(document["pairingsSenderId"]?.jsonPrimitive?.content == senderId)
    }
}

internal fun Json.pairingMetadata(fields: JsonObject): OwnPairing {
    val item = decodeFromJsonElement<OwnPairing>(JsonObject(fields + ("key" to JsonNull)))
    require(item.pairingId.isNotBlank() && item.receiverId.isNotBlank())
    require(item.alias.isNotBlank() && item.alias.codePointCount(0, item.alias.length) <= 40)
    require(item.provisionalSessionId == null || item.provisionalSessionId.isNotBlank())
    require(item.encryptions in 2..4294967296L)
    return item
}

internal fun Json.normalizeSnapshot(document: JsonObject): JsonObject {
    val authority = snapshotAuthority(document)
    return JsonObject(
        document + mapOf(
            "version" to JsonPrimitive(2),
            "renewal" to encodeToJsonElement(authority.renewal),
            "refreshState" to encodeToJsonElement(authority.refreshState),
            "closedSenderIds" to encodeToJsonElement(authority.closedSenderIds),
            "restoration" to encodeToJsonElement(authority.restoration),
            "pairingsRevision" to (document["pairingsRevision"] ?: JsonPrimitive(0L)),
            "pairingsListed" to (document["pairingsListed"] ?: JsonPrimitive(false)),
            "pairings" to JsonArray(
                document["pairings"]?.jsonArray.orEmpty().map { entry ->
                    val fields = entry.jsonObject
                    JsonObject(
                        fields + encodeToJsonElement(pairingMetadata(fields)).jsonObject +
                            ("key" to (fields["key"] ?: JsonNull))
                    )
                }
            )
        )
    )
}
