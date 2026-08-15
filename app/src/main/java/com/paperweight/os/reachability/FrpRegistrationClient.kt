package com.paperweight.os.reachability

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the real system.pape registration contract, read directly out of
 * `bud-diaz/paperweightv1`'s `src/api/dashboard.js` (`registerTelemetrySecretForSlug`/
 * `createFrpTunnelWithSecret`) during this session — resolving the plan doc's
 * previously-open item. Two calls:
 *  1. POST /api/modules/paperweight/register — trust-on-first-use slug claim.
 *  2. POST /api/modules/paperweight/frp/tunnel/create — issues frpc credentials,
 *     authenticated with the secret from step 1 via the `x-telemetry-secret` header.
 */
class FrpRegistrationClient(
    private val baseUrl: String = DEFAULT_PAPE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun register(slug: String, stationKey: String, secret: String) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RegisterRequest(slug, stationKey, secret)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/modules/paperweight/register")
            .post(body)
            .build()
        executeOrThrow(request) { /* 2xx body carries nothing this client needs */ }
    }

    suspend fun createTunnel(slug: String, stationKey: String, secret: String): FrpTunnelCredentials = withContext(Dispatchers.IO) {
        val body = json.encodeToString(CreateTunnelRequest(slug, stationKey)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$baseUrl/api/modules/paperweight/frp/tunnel/create")
            .addHeader("x-telemetry-secret", secret)
            .post(body)
            .build()
        var credentials: FrpTunnelCredentials? = null
        executeOrThrow(request) { bodyText ->
            val payload = json.decodeFromString<TunnelResponse>(bodyText)
            credentials = FrpTunnelCredentials(
                hostname = payload.hostname ?: throw unexpectedResponse(),
                serverAddr = payload.serverAddr ?: throw unexpectedResponse(),
                serverPort = payload.serverPort ?: throw unexpectedResponse(),
                authToken = payload.authToken ?: throw unexpectedResponse(),
                proxyName = payload.proxyName ?: throw unexpectedResponse(),
                subdomain = payload.subdomain ?: throw unexpectedResponse(),
            )
        }
        credentials ?: throw unexpectedResponse()
    }

    private fun unexpectedResponse() = FrpRegistrationException("system.pape returned an unexpected FRP response")

    private fun executeOrThrow(request: Request, onSuccess: (String) -> Unit) {
        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                when {
                    response.code == 409 -> {
                        val message = runCatching { json.decodeFromString<ErrorResponse>(bodyText).error }.getOrNull()
                        throw FrpRegistrationException(message ?: "Slug already claimed by another station", conflict = true)
                    }
                    !response.isSuccessful -> throw FrpRegistrationException("system.pape request failed (HTTP ${response.code})")
                    else -> onSuccess(bodyText)
                }
            }
        } catch (error: IOException) {
            throw FrpRegistrationException("Could not reach system.pape: ${error.message}", cause = error)
        }
    }

    @Serializable private data class RegisterRequest(val slug: String, val stationKey: String, val secret: String)
    @Serializable private data class CreateTunnelRequest(val slug: String, val stationKey: String)
    @Serializable private data class ErrorResponse(val error: String? = null)
    @Serializable private data class TunnelResponse(
        val hostname: String? = null,
        val serverAddr: String? = null,
        val serverPort: Int? = null,
        val authToken: String? = null,
        val proxyName: String? = null,
        val subdomain: String? = null,
    )

    companion object {
        // Matches paperweightv1's own config.js default for PAPE_URL — the A12
        // registers against the same frps the real Studio stations tunnel through.
        const val DEFAULT_PAPE_URL = "https://system.paperweighthq.com"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class FrpRegistrationException(
    message: String,
    val conflict: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)
