package com.tcwcontrol.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cc.teracom.app.android.model.TController
import cc.teracom.app.android.model.TSensor
import com.tcwcontrol.core.legacy.LegacyDevice
import com.tcwcontrol.core.legacy.LegacyDevices
import java.io.File
import java.io.InputStream
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/** What the previous TCW Control app had saved on this phone. */
data class LegacyState(val devices: List<LegacyDevice>, val refreshIndex: Int?)

/**
 * Reads the device list of the TCW Control app this one replaces (same package, so its files are
 * still in our data folder after the update). Two generations are supported:
 *
 * - the .NET MAUI app (3.x): MAUI Preferences key "devices" holds an XmlSerializer list, the
 *   passwords are in MAUI SecureStorage (EncryptedSharedPreferences) as "device_password_<Id>";
 * - the original Java app: a serialized TController[] in files/teracom.state.
 *
 * Nothing is deleted, so the old data stays as it was.
 */
class LegacyImport(private val context: Context) {
    private val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

    fun read(): LegacyState? = runCatching { readMaui() }.getOrNull() ?: runCatching { readJava() }.getOrNull()

    private fun readMaui(): LegacyState? {
        val names = prefsDir.listFiles { f: File -> f.name.endsWith(".xml") }.orEmpty().map { it.name.removeSuffix(".xml") }
        // Look in every preferences file: MAUI's default file name depends on its version.
        for (name in names) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (prefs.contains(KEYSET_KEY)) continue // the encrypted SecureStorage file
            val xml = prefs.all[LegacyDevices.DEVICES_KEY] as? String ?: continue
            val devices = LegacyDevices.parseMauiXml(xml)
            if (devices.isEmpty()) continue
            val secure = secureStores(names)
            val withPasswords = devices.map { d ->
                val stored = if (d.id.isEmpty()) null else secure.firstNotNullOfOrNull { store ->
                    runCatching { store.getString(LegacyDevices.PASSWORD_KEY_PREFIX + d.id, null) }.getOrNull()
                }
                d.copy(password = stored ?: d.password)
            }
            return LegacyState(withPasswords, prefs.all[LegacyDevices.REFRESH_KEY] as? Int)
        }
        return null
    }

    /** MAUI SecureStorage files; the expected name first, then any other encrypted preferences file. */
    private fun secureStores(names: List<String>): List<SharedPreferences> {
        val expected = "${context.packageName}.microsoft.maui.essentials.preferences"
        val candidates = names.filter {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).contains(KEYSET_KEY)
        }.sortedByDescending { it == expected }
        if (candidates.isEmpty()) return emptyList()
        val masterKey = runCatching {
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        }.getOrNull() ?: return emptyList()
        return candidates.mapNotNull { name ->
            runCatching {
                EncryptedSharedPreferences.create(
                    context,
                    name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
        }
    }

    private fun readJava(): LegacyState? {
        val file = File(context.filesDir, JAVA_STATE_FILE)
        if (!file.isFile) return null
        val controllers = file.inputStream().use { input ->
            JavaStateInput(input).readObject() as? Array<*>
        } ?: return null
        val devices = controllers.filterIsInstance<TController>().mapNotNull { c ->
            val address = c.deviceIp?.trim().orEmpty()
            if (address.isEmpty()) return@mapNotNull null
            LegacyDevice(
                id = "",
                address = address,
                port = c.devicePort?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 80,
                description = c.location.orEmpty(),
                username = c.username.orEmpty(),
                password = c.password.orEmpty(),
                isDemo = false,
            )
        }
        return if (devices.isEmpty()) null else LegacyState(devices, refreshIndex = null)
    }

    /** Reads only the classes the old app wrote, nothing else. */
    private class JavaStateInput(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (desc.name !in ALLOWED) throw InvalidClassException(desc.name, "not part of the old app's state")
            return super.resolveClass(desc)
        }
    }

    companion object {
        private const val JAVA_STATE_FILE = "teracom.state"
        private const val KEYSET_KEY = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        private val ALLOWED = setOf(
            "[L${TController::class.java.name};",
            TController::class.java.name,
            TSensor::class.java.name,
            "java.util.Vector",
            "[Ljava.lang.Object;",
            "java.lang.String",
        )
    }
}
