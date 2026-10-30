package com.tcwcontrol.app.data

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Packs a device list into QR-code text: a prefix, then the JSON gzipped and Base64-encoded,
 * which fits roughly three times more devices into one code than plain JSON.
 */
object QrCodec {
    private const val PREFIX = "TCWC1:"

    fun encode(json: String): String {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.toByteArray()) }
        }.toByteArray()
        return PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** The JSON inside a scanned code; plain JSON is accepted too. */
    fun decode(text: String): String {
        if (!text.startsWith(PREFIX)) return text
        val bytes = Base64.decode(text.removePrefix(PREFIX), Base64.NO_WRAP)
        return GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
    }
}
