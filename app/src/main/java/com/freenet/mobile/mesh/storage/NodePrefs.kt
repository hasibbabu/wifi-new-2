package com.freenet.mobile.mesh.storage

import android.content.Context
import java.util.UUID

class NodePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("freenet_node", Context.MODE_PRIVATE)

    fun stableInstallId(): String {
        val existing = prefs.getString("install_id", null)
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("install_id", id).apply()
        return id
    }
}
