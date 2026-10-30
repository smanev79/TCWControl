package com.tcwcontrol.core.net

import com.tcwcontrol.core.model.DeviceFamily
import com.tcwcontrol.core.model.DeviceStatus
import com.tcwcontrol.core.parser.StatusParser
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where and how to reach one device. */
data class Endpoint(
    val host: String,
    val port: Int = 80,
    val username: String = "",
    val password: String = "",
)

class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Blocking client; call it from a background dispatcher. */
class TeracomClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    fun fetchStatus(endpoint: Endpoint): DeviceStatus = StatusParser.parse(get(endpoint, null))

    /** Sends a relay command and returns the status the device answers with. */
    fun controlRelay(
        endpoint: Endpoint,
        family: DeviceFamily,
        relayIndex: Int,
        action: RelayAction,
    ): DeviceStatus = StatusParser.parse(get(endpoint, RelayCommand.query(family, relayIndex, action)))

    private fun get(endpoint: Endpoint, query: String?): ByteArray {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host(endpoint.host.trim())
            .port(endpoint.port)
            .addPathSegment("status.xml")
            .apply { if (query != null) encodedQuery(query) }
            .build()
        val request = Request.Builder().url(url).apply {
            if (endpoint.username.isNotEmpty()) {
                header("Authorization", Credentials.basic(endpoint.username, endpoint.password))
            }
        }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            return response.body?.bytes() ?: throw IOException("Empty response")
        }
    }
}
