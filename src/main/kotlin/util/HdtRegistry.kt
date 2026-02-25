package com.example.com.util

import java.net.ServerSocket

object HdtRegistry {
    private val registry: MutableMap<String, Int> = mutableMapOf()
    const val HDT_HTTP_HOST = "127.0.0.1"

    fun register(id: String) {
        registry[id] = findFreePort()
    }

    fun getRegisteredIds(): Set<String> {
        return registry.keys
    }

    fun getPort(id: String): Int? {
        return registry[id]
    }

    fun getUrl(id: String): String? {
        return getPort(id).let { port ->
            "http://$HDT_HTTP_HOST:$port"
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}