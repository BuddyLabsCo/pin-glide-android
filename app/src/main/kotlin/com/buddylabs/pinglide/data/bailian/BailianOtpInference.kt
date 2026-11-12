package com.buddylabs.pinglide.data.bailian

import android.util.Log
import kotlinx.coroutines.CancellationException

class BailianOtpInference(
    private val credentials: BailianCredentials,
    private val chat: BailianChat
) {
    suspend fun infer(smsText: String, session: Long): String? = try {
        extract(smsText, session)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Log.w("PinGlide", "OTP extraction unavailable")
        null
    }

    private suspend fun extract(smsText: String, session: Long): String? {
        repeat(2) { attempt ->
            val lease = credentials.acquire(session)
            check(credentials.isCurrent(lease))
            try {
                val otp = chat.complete(lease.value, smsText) { credentials.isCurrent(lease) }
                return otp.takeIf { credentials.isCurrent(lease) }
            } catch (expired: ProviderKeyExpiredException) {
                credentials.invalidate(lease)
                if (attempt == 1) throw expired
            }
        }
        return null
    }
}
