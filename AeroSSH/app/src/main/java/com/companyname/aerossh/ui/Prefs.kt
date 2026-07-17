package com.companyname.aerossh.ui

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val NAME = "aerossh_prefs"

    private fun get(context: Context): SharedPreferences {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    // Theme
    fun getTheme(context: Context): String = get(context).getString("theme", "dark") ?: "dark"
    fun setTheme(context: Context, value: String) = get(context).edit().putString("theme", value).apply()

    fun getAccentColor(context: Context): Int = get(context).getInt("accent_color", 0xFF58A6FF.toInt())
    fun setAccentColor(context: Context, value: Int) = get(context).edit().putInt("accent_color", value).apply()

    // Terminal
    fun getFontSize(context: Context): Float = get(context).getFloat("font_size", 14f)
    fun setFontSize(context: Context, value: Float) = get(context).edit().putFloat("font_size", value).apply()

    fun getLineSpacing(context: Context): Float = get(context).getFloat("line_spacing", 1.3f)
    fun setLineSpacing(context: Context, value: Float) = get(context).edit().putFloat("line_spacing", value).apply()

    fun getScrollback(context: Context): Int = get(context).getInt("scrollback_lines", 5000)
    fun setScrollback(context: Context, value: Int) = get(context).edit().putInt("scrollback_lines", value).apply()

    fun getCursor(context: Context): String = get(context).getString("cursor_style", "block") ?: "block"
    fun setCursor(context: Context, value: String) = get(context).edit().putString("cursor_style", value).apply()

    // Connection
    fun getTimeout(context: Context): Int = get(context).getInt("connection_timeout", 30)
    fun setTimeout(context: Context, value: Int) = get(context).edit().putInt("connection_timeout", value).apply()

    fun getKeepalive(context: Context): Int = get(context).getInt("keepalive_interval", 0)
    fun setKeepalive(context: Context, value: Int) = get(context).edit().putInt("keepalive_interval", value).apply()

    fun getAutoReconnect(context: Context): Boolean = get(context).getBoolean("auto_reconnect", false)
    fun setAutoReconnect(context: Context, value: Boolean) = get(context).edit().putBoolean("auto_reconnect", value).apply()

    fun getEncoding(context: Context): String = get(context).getString("encoding", "UTF-8") ?: "UTF-8"
    fun setEncoding(context: Context, value: String) = get(context).edit().putString("encoding", value).apply()

    // Log
    fun getLogSessions(context: Context): Boolean = get(context).getBoolean("log_sessions", false)
    fun setLogSessions(context: Context, value: Boolean) = get(context).edit().putBoolean("log_sessions", value).apply()
}
