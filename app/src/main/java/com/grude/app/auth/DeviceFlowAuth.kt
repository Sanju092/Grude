package com.grude.app.auth

import com.grude.app.model.DeviceCodeResponse
import com.grude.app.model.OAuthTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Serverless GitHub Device Flow & GitHub App Token Refresh client.
 * Requires no Client Secret on device (works for both OAuth Apps with Device Flow enabled
 * and GitHub Apps).
 */
class DeviceFlowAuth(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestDeviceCode(
        clientId: String,
        scope: String = "repo workflow read:user"
    ): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", scope)
                .build()

            val request = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Device code request failed (${resp.code}): $raw")
                json.decodeFromString<DeviceCodeResponse>(raw)
            }
        }
    }

    suspend fun pollForToken(
        clientId: String,
        deviceCode: String,
        intervalSeconds: Int = 5,
        maxAttempts: Int = 60
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            var waitSec = intervalSeconds.coerceAtLeast(5)
            repeat(maxAttempts) {
                delay(waitSec * 1000L)
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()

                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()

                val parsed = httpClient.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    json.decodeFromString<OAuthTokenResponse>(raw)
                }

                when {
                    !parsed.accessToken.isNullOrBlank() -> return@runCatching parsed
                    parsed.error == "authorization_pending" -> Unit
                    parsed.error == "slow_down" -> waitSec += 5
                    else -> error(parsed.errorDescription ?: parsed.error ?: "Device authorization failed")
                }
            }
            error("Device code expired before authorization completed.")
        }
    }

    /**
     * Refreshes an expiring GitHub App user access token via refresh_token.
     */
    suspend fun refreshGitHubAppToken(
        clientId: String,
        refreshToken: String,
        relayServerUrl: String = ""
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = if (relayServerUrl.isNotBlank()) {
                "${relayServerUrl.trimEnd('/')}/refresh"
            } else {
                "https://github.com/login/oauth/access_token"
            }

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Token refresh failed (${resp.code}): $raw")
                json.decodeFromString<OAuthTokenResponse>(raw)
            }
        }
    }
}
