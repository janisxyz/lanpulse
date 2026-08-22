package com.lanpulse.app.data

import android.content.Context

class DeviceNamesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device_names", Context.MODE_PRIVATE)

    fun get(mac: String?, ip: String): String? {
        mac?.lowercase()?.let { key ->
            prefs.getString("mac:$key", null)?.let { return it }
        }
        return prefs.getString("ip:$ip", null)
    }

    fun set(mac: String?, ip: String, name: String) {
        val trimmed = name.trim()
        prefs.edit().apply {
            if (trimmed.isEmpty()) {
                remove("ip:$ip")
                mac?.lowercase()?.let { remove("mac:$it") }
            } else {
                putString("ip:$ip", trimmed)
                mac?.lowercase()?.let { putString("mac:$it", trimmed) }
            }
            apply()
        }
    }
}
