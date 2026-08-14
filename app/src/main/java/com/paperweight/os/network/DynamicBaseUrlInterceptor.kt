package com.paperweight.os.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

// Retrofit needs a syntactically valid base URL at build time, but the real
// one isn't known until the operator pairs the device (see pairing/). This
// rewrites the scheme/host/port of every outgoing request to the
// currently-paired station, leaving Retrofit's declared paths untouched.
class DynamicBaseUrlInterceptor(private val sessionStore: SessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val stationUrl = sessionStore.baseUrl?.toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val rewrittenUrl = original.url.newBuilder()
            .scheme(stationUrl.scheme)
            .host(stationUrl.host)
            .port(stationUrl.port)
            .build()

        return chain.proceed(original.newBuilder().url(rewrittenUrl).build())
    }
}
