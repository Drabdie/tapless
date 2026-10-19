package com.aiphoneagent.mcp

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

data class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: String,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    companion object {
        fun json(body: String, status: Int = 200) =
            HttpResponse(status, "application/json; charset=utf-8", body)

        fun text(body: String, status: Int = 200) =
            HttpResponse(status, "text/plain; charset=utf-8", body)

        fun noContent() = HttpResponse(204, "text/plain; charset=utf-8", "")
    }
}

/**
 * A tiny, dependency-free HTTP/1.1 server bound to localhost. Only what the MCP transport
 * needs: parse a request, hand it to a suspend handler, write a JSON response, close.
 */
class MiniHttpServer(
    private val requestedPort: Int,
    private val handler: suspend (HttpRequest) -> HttpResponse,
) {
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    @Volatile private var running = false
    @Volatile private var actualPort = requestedPort

    val isRunning: Boolean get() = running
    fun boundPort(): Int = actualPort

    fun start(): Boolean {
        if (running) return true
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", requestedPort))
            serverSocket = socket
            actualPort = socket.localPort
            running = true
            val localScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope = localScope
            localScope.launch {
                while (running) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        if (running) continue else break
                    }
                    localScope.launch { handleClient(client) }
                }
            }
            true
        } catch (_: Exception) {
            running = false
            false
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 8000
                val input = BufferedInputStream(client.getInputStream())
                val request = readRequest(input) ?: return
                val response = try {
                    handler(request)
                } catch (error: Exception) {
                    HttpResponse.json("{\"error\":\"${escape(error.message)}\"}", 500)
                }
                writeResponse(client, response)
            } catch (_: Exception) {
                // Connection dropped; nothing to do.
            }
        }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0]
        val path = parts[1]
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) {
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(bytes, read, length - read)
                if (count == -1) break
                read += count
            }
            String(bytes, 0, read, Charsets.UTF_8)
        } else {
            ""
        }
        return HttpRequest(method, path, headers, body)
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        var readAny = false
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (readAny) builder.toString() else null
            readAny = true
            if (byte == '\n'.code) break
            builder.append(byte.toChar())
        }
        if (builder.isNotEmpty() && builder.last() == '\r') builder.deleteCharAt(builder.length - 1)
        return builder.toString()
    }

    private fun writeResponse(socket: Socket, response: HttpResponse) {
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        val statusText = when (response.status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ').append(statusText).append("\r\n")
            append("Content-Type: ").append(response.contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            response.extraHeaders.forEach { (key, value) -> append(key).append(": ").append(value).append("\r\n") }
            append("\r\n")
        }
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun escape(value: String?): String = value?.replace("\"", "'")?.replace("\n", " ") ?: "error"
}
