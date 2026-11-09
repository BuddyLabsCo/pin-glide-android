package com.buddylabs.pinglide.data.service

import java.io.IOException
import java.util.Locale
import okhttp3.Call
import okhttp3.Callback
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

internal fun identityCalls(client: Call.Factory) = Call.Factory { request ->
    val body = checkNotNull(request.body)
    val oneShotBody = object : RequestBody() {
        override fun contentType() = body.contentType()

        override fun contentLength() = body.contentLength()

        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)

        override fun isOneShot() = true
    }
    val call = client.newCall(request.newBuilder().method(request.method, oneShotBody).build())
    object : Call by call {
        override fun enqueue(responseCallback: Callback) =
            call.enqueue(IdentityResponseCallback(responseCallback))
    }
}

private class IdentityResponseCallback(private val callback: Callback) : Callback by callback {
    override fun onResponse(call: Call, response: Response) {
        val mediaType = response.header("Content-Type")?.substringBefore(";")?.lowercase(Locale.US)
        if (response.isSuccessful && mediaType != null &&
            !(mediaType.startsWith("application/") && mediaType.endsWith("json"))
        ) {
            response.close()
            callback.onFailure(
                call,
                IOException("Identity service returned an unsupported response")
            )
        } else {
            callback.onResponse(call, response)
        }
    }
}
