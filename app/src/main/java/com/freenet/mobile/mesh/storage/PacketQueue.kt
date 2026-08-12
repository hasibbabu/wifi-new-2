package com.freenet.mobile.mesh.storage

import android.content.Context
import com.freenet.mobile.mesh.protocol.MeshEnvelope
import org.json.JSONArray
import java.util.concurrent.ConcurrentLinkedQueue

class PacketQueue(context: Context) {
    private val prefs = context.getSharedPreferences("freenet_queue", Context.MODE_PRIVATE)
    private val queue = ConcurrentLinkedQueue<String>()

    init {
        val saved = JSONArray(prefs.getString("packets", "[]"))
        for (i in 0 until saved.length()) queue.add(saved.getString(i))
    }

    @Synchronized
    fun enqueue(packet: MeshEnvelope) {
        queue.add(String(packet.encode(), Charsets.UTF_8))
        persist()
    }

    @Synchronized
    fun drain(): List<MeshEnvelope> {
        val result = mutableListOf<MeshEnvelope>()
        while (true) {
            val raw = queue.poll() ?: break
            try { result.add(MeshEnvelope.decode(raw.toByteArray())) } catch (_: Exception) {}
        }
        persist()
        return result
    }

    @Synchronized
    fun size() = queue.size

    private fun persist() {
        val a = JSONArray()
        queue.take(1000).forEach { a.put(it) }
        prefs.edit().putString("packets", a.toString()).apply()
    }
}
