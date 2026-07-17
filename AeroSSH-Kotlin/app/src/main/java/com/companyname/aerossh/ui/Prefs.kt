package com.companyname.aerossh.ui

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun get(c: Context): SharedPreferences = c.getSharedPreferences("aerossh_prefs", Context.MODE_PRIVATE)
    fun getTheme(c: Context): String = get(c).getString("theme", "dark") ?: "dark"; fun setTheme(c: Context, v: String) = get(c).edit().putString("theme", v).apply()
    fun getFontSize(c: Context): Float = get(c).getFloat("font_size", 14f); fun setFontSize(c: Context, v: Float) = get(c).edit().putFloat("font_size", v).apply()
    fun getLineSpacing(c: Context): Float = get(c).getFloat("line_spacing", 1.3f); fun setLineSpacing(c: Context, v: Float) = get(c).edit().putFloat("line_spacing", v).apply()
    fun getScrollback(c: Context): Int = get(c).getInt("scrollback_lines", 5000); fun setScrollback(c: Context, v: Int) = get(c).edit().putInt("scrollback_lines", v).apply()
    fun getCursor(c: Context): String = get(c).getString("cursor_style", "block") ?: "block"; fun setCursor(c: Context, v: String) = get(c).edit().putString("cursor_style", v).apply()
    fun getTimeout(c: Context): Int = get(c).getInt("connection_timeout", 30); fun setTimeout(c: Context, v: Int) = get(c).edit().putInt("connection_timeout", v).apply()
    fun getKeepalive(c: Context): Int = get(c).getInt("keepalive_interval", 0); fun setKeepalive(c: Context, v: Int) = get(c).edit().putInt("keepalive_interval", v).apply()
    fun getAutoReconnect(c: Context): Boolean = get(c).getBoolean("auto_reconnect", false); fun setAutoReconnect(c: Context, v: Boolean) = get(c).edit().putBoolean("auto_reconnect", v).apply()
    fun getEncoding(c: Context): String = get(c).getString("encoding", "UTF-8") ?: "UTF-8"; fun setEncoding(c: Context, v: String) = get(c).edit().putString("encoding", v).apply()
    fun getLogSessions(c: Context): Boolean = get(c).getBoolean("log_sessions", false); fun setLogSessions(c: Context, v: Boolean) = get(c).edit().putBoolean("log_sessions", v).apply()
}
