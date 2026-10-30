package com.tcwcontrol.app.ui

import android.content.Context
import com.tcwcontrol.app.R
import com.tcwcontrol.core.net.HttpStatusException
import com.tcwcontrol.core.parser.UnsupportedDeviceException
import org.xml.sax.SAXException

/** Turns a failed request into a short message for the user. */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is HttpStatusException -> if (code == 401 || code == 403) {
        context.getString(R.string.error_auth)
    } else {
        context.getString(R.string.error_connection, "HTTP $code")
    }
    is UnsupportedDeviceException, is SAXException -> context.getString(R.string.error_format)
    else -> context.getString(R.string.error_connection, localizedMessage ?: javaClass.simpleName)
}
