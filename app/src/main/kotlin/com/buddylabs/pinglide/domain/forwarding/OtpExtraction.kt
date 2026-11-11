package com.buddylabs.pinglide.domain.forwarding

import kotlinx.coroutines.CancellationException

class OtpExtraction(private val infer: suspend (String) -> String?) {
    suspend fun extract(smsText: String): String {
        if (!candidate.containsMatchIn(smsText)) return ""
        return try {
            infer(smsText)?.trim()?.takeIf { otp ->
                result.matches(otp) && smsText.contains(otp)
            }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        val candidate =
            Regex("(?<![A-Za-z0-9])(?=[A-Za-z0-9]{0,7}[0-9])[A-Za-z0-9]{4,8}(?![A-Za-z0-9])")
        val result = Regex("[A-Za-z0-9]{4,8}")
    }
}
