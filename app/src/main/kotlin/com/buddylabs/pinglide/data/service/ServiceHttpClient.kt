package com.buddylabs.pinglide.data.service

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient

internal fun serviceHttpClient(debug: Boolean): OkHttpClient = OkHttpClient.Builder()
    .connectionSpecs(
        if (debug) {
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
        } else {
            listOf(ConnectionSpec.MODERN_TLS)
        }
    )
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()
