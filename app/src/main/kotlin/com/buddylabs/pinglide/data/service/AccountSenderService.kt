package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AuthorizationCheckException
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.SenderInventoryService
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.SenderRegistration
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import kotlinx.serialization.SerializationException
import okhttp3.Call

class AccountSenderService(private val baseUrl: String, private val client: Call.Factory) :
    SenderInventoryService {
    override suspend fun list(identity: SenderIdentity): List<AccountSender> {
        if (baseUrl.isBlank()) throw AuthorizationCheckException()
        val api = IdentityApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { identity.management.token }
        }
        val response = try {
            api.getSelfSendersWithHttpInfo()
        } catch (_: IOException) {
            throw AuthorizationCheckException()
        } catch (_: SerializationException) {
            throw AuthorizationCheckException()
        }
        if (response is ClientError) requireCurrentSenderAccess(response.statusCode, response.body)
        if (response !is Success || response.statusCode != 200) throw AuthorizationCheckException()
        val items = response.data?.items ?: throw AuthorizationCheckException()
        if (items.none { it.senderId == identity.senderId }) throw AuthorizationCheckException()
        return mapSenders(items)
    }
}

private fun mapSenders(items: List<SenderRegistration>): List<AccountSender> {
    try {
        require(items.map { it.senderId }.distinct().size == items.size)
        items.forEach {
            require(it.senderId.isNotBlank())
            require(it.deviceName.isNotBlank() && it.deviceName == it.deviceName.trim())
            require(it.deviceName.codePointCount(0, it.deviceName.length) in 1..64)
            require(it.state == SenderRegistration.State.active && it.revokedAt == null)
            Instant.parse(it.createdAt)
        }
        return items.sortedWith(
            compareByDescending<SenderRegistration> {
                Instant.parse(it.createdAt)
            }.thenBy { it.senderId }
        ).map { AccountSender(it.senderId, it.deviceName) }
    } catch (_: IllegalArgumentException) {
        throw AuthorizationCheckException()
    } catch (_: DateTimeException) {
        throw AuthorizationCheckException()
    }
}
