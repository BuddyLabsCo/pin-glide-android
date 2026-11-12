package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.SenderContext
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal fun Json.readRestoration(document: JsonObject): SenderContext? {
    val restoration = document["restoration"]?.let { decodeFromJsonElement<SenderContext?>(it) }
    require(
        restoration == null ||
            (restoration.accountId.isNotBlank() && restoration.senderId.isNotBlank())
    )
    return restoration
}

internal fun IdentitySnapshot.loggedOut() = copy(
    generation = if (refreshState ==
        RefreshState.LoggedOut
    ) {
        generation
    } else {
        Math.addExact(generation, 1)
    },
    identity = null,
    renewal = null,
    refreshState = RefreshState.LoggedOut,
    restoration = identity?.let { SenderContext(it.accountId, it.senderId) } ?: restoration
)

internal fun Json.logoutDocument(document: JsonObject, snapshot: IdentitySnapshot): JsonObject {
    val retained = document.filterKeys {
        it in
            setOf("pairingsAccountId", "pairingsSenderId", "pairingsRevision")
    }
    val pairings = (document["pairings"] as? JsonArray).orEmpty().map { entry ->
        JsonObject(
            entry.jsonObject.filterKeys {
                it in
                    setOf(
                        "pairingId", "receiverId", "alias", "enabled", "key", "keyFailed",
                        "protectedKey", "provisionalSessionId", "encryptions"
                    )
            }
        )
    }
    return JsonObject(
        retained + mapOf(
            "version" to JsonPrimitive(IDENTITY_SNAPSHOT_VERSION),
            "generation" to JsonPrimitive(snapshot.generation),
            "identity" to JsonNull,
            "renewal" to JsonNull,
            "refreshState" to encodeToJsonElement(snapshot.refreshState),
            "closedSenderIds" to encodeToJsonElement(snapshot.closedSenderIds),
            "restoration" to encodeToJsonElement(snapshot.restoration),
            "pairingsListed" to JsonPrimitive(false),
            "pairings" to JsonArray(pairings)
        )
    )
}

internal fun IdentitySnapshot.retainsSender(identity: SenderIdentity): Boolean =
    (this.identity?.let { SenderContext(it.accountId, it.senderId) } ?: restoration) ==
        SenderContext(identity.accountId, identity.senderId)

internal fun Json.loginDocument(
    document: JsonObject,
    original: IdentitySnapshot,
    identity: SenderIdentity
): JsonObject {
    val base = if (original.retainsSender(identity)) {
        document
    } else {
        JsonObject(emptyMap())
    }
    return JsonObject(
        base + mapOf(
            "version" to JsonPrimitive(IDENTITY_SNAPSHOT_VERSION),
            "generation" to JsonPrimitive(Math.addExact(original.generation, 1)),
            "identity" to encodeToJsonElement(identity),
            "renewal" to JsonNull,
            "restoration" to JsonNull,
            "closedSenderIds" to encodeToJsonElement(original.closedSenderIds),
            "refreshState" to encodeToJsonElement(RefreshState.Active)
        )
    )
}
