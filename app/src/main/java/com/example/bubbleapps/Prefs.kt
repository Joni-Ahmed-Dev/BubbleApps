package com.example.bubbleapps
import android.content.Context
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("bubble", 0)
    fun apps(c: Context) = sp(c).getString("apps", "")!!.split(",").filter { it.isNotBlank() }
    fun setApps(c: Context, l: List<String>) = sp(c).edit().putString("apps", l.joinToString(",")).apply()
    fun size(c: Context) = sp(c).getInt("size", 60)
    fun alpha(c: Context) = sp(c).getInt("alpha", 100)
    fun color(c: Context) = sp(c).getInt("color", 0xFF6C63FF.toInt())
    fun floating(c: Context) = sp(c).getBoolean("floating", true)
    fun put(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun putB(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
}
