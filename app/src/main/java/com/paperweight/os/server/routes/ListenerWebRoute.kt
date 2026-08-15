package com.paperweight.os.server.routes

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/** Serves the vendored static listener player page out of `assets/listener/`. */
class ListenerWebRoute(private val context: Context) {
    fun serve(uri: String): NanoHTTPD.Response {
        val assetFile = ASSET_MAP[uri] ?: return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found",
        )
        return try {
            val stream = context.assets.open("listener/$assetFile")
            NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, mimeTypeFor(assetFile), stream)
        } catch (error: IOException) {
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun mimeTypeFor(assetFile: String): String = when {
        assetFile.endsWith(".html") -> "text/html"
        assetFile.endsWith(".js") -> "application/javascript"
        assetFile.endsWith(".css") -> "text/css"
        else -> "application/octet-stream"
    }

    private companion object {
        val ASSET_MAP = mapOf(
            "/" to "index.html",
            "/index.html" to "index.html",
            "/player.js" to "player.js",
            "/styles.css" to "styles.css",
            "/hls.min.js" to "hls.min.js",
        )
    }
}
