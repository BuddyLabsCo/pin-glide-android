package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.SenderContext
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginDocumentTest {
    private val json = Json
    private val original = IdentitySnapshot(
        generation = 2,
        refreshState = RefreshState.LoggedOut,
        closedSenderIds = setOf("closed-sender"),
        restoration = SenderContext("account", "sender")
    )
    private val identity = SenderIdentity(
        "account",
        "sender",
        ManagementCredential("management", "2030-01-01T01:00:00Z"),
        UploadCredential("upload", "2030-01-01T01:00:00Z"),
        RefreshCredential("refresh", "2030-01-02T00:00:00Z")
    )
    private val document = json.decodeFromString<JsonObject>(
        """{
            "version":1,"generation":2,"identity":null,"renewal":null,
            "refreshState":"LoggedOut","closedSenderIds":["closed-sender"],
            "restoration":{"accountId":"account","senderId":"sender"},
            "pairingsAccountId":"account","pairingsSenderId":"sender",
            "pairingsRevision":4,"pairingsListed":false,
            "pairings":[{"pairingId":"pairing","receiverId":"receiver",
                "alias":"Laptop","enabled":false,"key":[1,2,3]}],
            "oldAccountProfile":{"nickname":"Old owner"}
        }"""
    )

    @Test
    fun missingRestorationProofCannotReattachPairingKeys() {
        val accepted = json.loginDocument(document, original.copy(restoration = null), identity)

        assertFalse(accepted.containsKey("pairings"))
    }

    @Test
    fun verifiedSameSenderKeepsPairingKeysAliasesAndReceivePreferences() {
        val accepted = json.loginDocument(document, original, identity)

        assertTrue(original.retainsSender(identity))
        assertEquals(document.getValue("pairings"), accepted.getValue("pairings"))
        assertEquals(document.getValue("pairingsRevision"), accepted.getValue("pairingsRevision"))
        assertEquals("3", accepted.getValue("generation").jsonPrimitive.content)
    }

    @Test
    fun verifiedDifferentAccountStartsWithoutOldKeysOrAccountFields() {
        val replacement = SenderIdentity(
            "other-account",
            "other-sender",
            identity.management,
            identity.upload,
            identity.refresh
        )

        val accepted = json.loginDocument(document, original, replacement)

        assertFalse(original.retainsSender(replacement))
        assertFalse(accepted.containsKey("pairings"))
        assertFalse(accepted.containsKey("pairingsAccountId"))
        assertFalse(accepted.containsKey("oldAccountProfile"))
        assertEquals(document.getValue("closedSenderIds"), accepted.getValue("closedSenderIds"))
        assertEquals(
            "other-account",
            accepted.getValue("identity").jsonObject.getValue("accountId").jsonPrimitive.content
        )
    }

    @Test
    fun homeRestorationKeepsOnlyTheCurrentlyVerifiedSenderContext() {
        val active = original.copy(identity = identity, restoration = null)

        assertTrue(active.retainsSender(identity))
        assertEquals(
            document.getValue("pairings"),
            json.loginDocument(document, active, identity).getValue("pairings")
        )
    }
}
