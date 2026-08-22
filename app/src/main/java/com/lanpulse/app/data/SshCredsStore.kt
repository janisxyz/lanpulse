package com.lanpulse.app.data

import android.content.Context

class SshCredsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ssh_creds", Context.MODE_PRIVATE)

    fun user(ip: String, fallback: String): String =
        prefs.getString("user:$ip", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("last_user", null)?.takeIf { it.isNotBlank() }
            ?: fallback

    fun password(ip: String): String = prefs.getString("pass:$ip", "") ?: ""

    fun port(ip: String): Int = prefs.getInt("port:$ip", 22).takeIf { it in 1..65535 } ?: 22

    fun remember(ip: String): Boolean = prefs.getBoolean("remember:$ip", false)

    fun save(ip: String, user: String, password: String, port: Int, rememberPassword: Boolean) {
        prefs.edit().apply {
            putString("user:$ip", user.trim())
            putString("last_user", user.trim())
            putInt("port:$ip", port)
            putBoolean("remember:$ip", rememberPassword)
            if (rememberPassword) putString("pass:$ip", password) else remove("pass:$ip")
            apply()
        }
    }
}
