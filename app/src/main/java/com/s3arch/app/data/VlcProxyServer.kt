package com.s3arch.app.data

import okhttp3.Response
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Petit serveur HTTP local (127.0.0.1) qui relaie le flux d'un fichier de la Freebox
 * en y ajoutant l'en-tete X-Fbx-App-Auth. VLC peut ainsi lire le fichier via une URL
 * locale, avec support des requetes Range (avance/recule dans la video).
 */
class VlcProxyServer {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var opener: ((encodedPath: String, range: String?) -> Response)? = null

    /** Demarre le proxy (si besoin) et renvoie le port local. */
    @Synchronized
    fun start(openFile: (encodedPath: String, range: String?) -> Response): Int {
        opener = openFile
        val existing = serverSocket
        if (existing != null && !existing.isClosed) return existing.localPort

        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = server
        thread(isDaemon = true, name = "s3arch-vlc-proxy") {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true, name = "s3arch-vlc-client") { handle(client) }
            }
        }
        return server.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            try {
                val input = client.getInputStream().buffered()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    writeError(client, 400, "Requete invalide")
                    return
                }
                val method = parts[0].uppercase()
                val target = parts[1]
                var range: String? = null
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    if (line.lowercase().startsWith("range:")) {
                        range = line.substringAfter(':').trim()
                    }
                }

                val query = target.substringAfter('?', "")
                val encodedPath = queryParam(query, "path")
                val name = queryParam(query, "name").orEmpty()
                val openFile = opener
                if (encodedPath.isNullOrBlank()) {
                    writeError(client, 400, "Parametre path manquant")
                    return
                }
                if (openFile == null) {
                    writeError(client, 503, "Proxy non initialise")
                    return
                }

                val response = try {
                    openFile(encodedPath, range)
                } catch (e: Exception) {
                    writeError(client, 502, e.message ?: "Freebox injoignable")
                    return
                }

                response.use { upstream -> relay(client, upstream, name, method) }
            } catch (e: Exception) {
                runCatching { writeError(client, 500, e.message ?: "Erreur du proxy") }
            }
        }
    }

    private fun relay(socket: Socket, upstream: Response, name: String, method: String) {
        val out = BufferedOutputStream(socket.getOutputStream())
        val body = upstream.body
        val contentType = upstream.header("Content-Type")?.takeIf { it.isNotBlank() }
            ?: contentTypeFor(name)
        val contentRange = upstream.header("Content-Range")
        val contentLength = body?.contentLength() ?: -1L

        val status = when {
            upstream.code == 416 -> "416 Range Not Satisfiable"
            upstream.code == 206 -> "206 Partial Content"
            upstream.isSuccessful -> "200 OK"
            else -> "${upstream.code} ${upstream.message.ifBlank { "Erreur" }}"
        }

        val headers = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Connection: close\r\n")
            if (!contentRange.isNullOrBlank()) {
                append("Content-Range: ").append(contentRange).append("\r\n")
            }
            if (contentLength >= 0) {
                append("Content-Length: ").append(contentLength).append("\r\n")
            }
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.ISO_8859_1))
        out.flush()

        if (method == "HEAD" || !upstream.isSuccessful) return
        val stream: InputStream = body?.byteStream() ?: return
        stream.use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
                out.flush()
            }
        }
        runCatching { socket.shutdownOutput() }
    }

    private fun writeError(socket: Socket, code: Int, message: String) {
        val payload = message.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code Erreur\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(head.plus("").toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        out.flush()
        runCatching { socket.shutdownOutput() }
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (builder.isEmpty()) null else builder.toString()
            if (c == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(c.toChar())
            if (builder.length > 8_192) return builder.toString()
        }
    }

    private fun queryParam(query: String, key: String): String? {
        if (query.isBlank()) return null
        return query.split('&').firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    private fun contentTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "srt" -> "application/x-subrip"
        else -> "application/octet-stream"
    }
}
