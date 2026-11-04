package com.athir.datatransfer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

object IdentityStore {
    private const val PREF_NAME = "athir_identity_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DISPLAY_NAME = "display_name"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getDeviceId(context: Context): String {
        val prefs = getPrefs(context)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDisplayName(context: Context): String {
        val prefs = getPrefs(context)
        var name = prefs.getString(KEY_DISPLAY_NAME, null)
        if (name.isNullOrBlank()) {
            val model = Build.MODEL ?: "Android"
            name = "أثير ($model)"
            prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
        }
        return name
    }

    fun setDisplayName(context: Context, newName: String) {
        if (newName.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_DISPLAY_NAME, newName.trim()).apply()
        }
    }
}
