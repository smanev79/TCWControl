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

/** Size of one HTTP exchange with a device, for traffic statistics. Header sizes are approximate. */
data class TrafficSample(
    val host: String,
    val port: Int,
    val sentBytes: Long,
    val receivedBytes: Long,
    val success: Boolean,
)

private const val REQUEST_LINE_OVERHEAD = 16 // "GET " + " HTTP/1.1\r\n" and the like

/** Blocking client; call it from a background dispatcher. */
class TeracomClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    /** Called after every request, successful or not. */
    private val onTraffic: (TrafficSample) -> Unit = {},
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
        val sent = request.url.toString().length + REQUEST_LINE_OVERHEAD + request.headers.byteCount()
        var received = 0L
        var success = false
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.bytes()
                received = response.headers.byteCount() + (body?.size ?: 0)
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                success = body != null
                return body ?: throw IOException("Empty response")
            }
        } finally {
            onTraffic(TrafficSample(endpoint.host, endpoint.port, sent, received, success))
        }
    }
}
