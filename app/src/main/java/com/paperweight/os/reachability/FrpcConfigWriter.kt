package com.paperweight.os.reachability

import android.content.Context
import java.io.File

/**
 * Writes `frpc.toml` byte-for-byte matching the shape paperweightv1's own
 * `src/runtime/frp-config.js::buildFrpcToml` writes, so this device
 * authenticates against the same frps gateway the same way. Written to
 * app-internal storage (`<filesDir>/tunnel/frpc.toml`), not the SD card —
 * it's regenerable, secret-bearing config, not something to round-trip
 * through SD backups (plan decision #12).
 */
object FrpcConfigWriter {
    fun write(context: Context, credentials: FrpTunnelCredentials, localPort: Int): File {
        assertSafe("serverAddr", credentials.serverAddr)
        assertSafe("authToken", credentials.authToken)
        assertSafe("proxyName", credentials.proxyName)
        assertSafe("subdomain", credentials.subdomain)
        require(credentials.serverPort in 1..65535) { "serverPort is invalid" }
        require(localPort in 1..65535) { "localPort is invalid" }

        val toml = buildString {
            appendLine("serverAddr = \"${credentials.serverAddr}\"")
            appendLine("serverPort = ${credentials.serverPort}")
            appendLine()
            appendLine("auth.method = \"token\"")
            appendLine("auth.token = \"${credentials.authToken}\"")
            appendLine()
            appendLine("[[proxies]]")
            appendLine("name = \"${credentials.proxyName}\"")
            appendLine("type = \"http\"")
            appendLine("localIP = \"127.0.0.1\"")
            appendLine("localPort = $localPort")
            appendLine("subdomain = \"${credentials.subdomain}\"")
        }

        val dir = File(context.filesDir, "tunnel").apply { mkdirs() }
        val file = File(dir, "frpc.toml")
        file.writeText(toml)
        return file
    }

    private fun assertSafe(label: String, value: String) {
        require(value.isNotBlank() && value.none { it == '\r' || it == '\n' || it == '"' || it == '#' }) { "$label is invalid" }
    }
}
