package com.s3arch.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Verifie le proxy local qui alimente VLC.
 *
 * C'est lui qui produisait un VLC muet : l'URL etait bien lancee, mais aucun flux
 * ne sortait du serveur. Ces tests executent de vrais sockets sur 127.0.0.1.
 */
class VlcProxyServerTest {

    private fun response(code: Int, body: String, contentType: String = "video/mp4"): Response =
        Response.Builder()
            .request(Request.Builder().url("http://127.0.0.1/upstream").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Partial Content")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    private fun source(name: String, size: Long = 0L) =
        VlcProxyServer.StreamSource(encodedPath = "TDpDc2N1ZQ==", name = name, size = size)

    private fun get(port: Int, path: String, range: String? = null): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 4_000)
            socket.soTimeout = 4_000
            val out = socket.getOutputStream()
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: 127.0.0.1:").append(port).append("\r\n")
                if (range != null) append("Range: ").append(range).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(request.toByteArray(Charsets.ISO_8859_1))
            out.flush()
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    @Test
    fun `le flux est relaya avec son nom de fichier`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ -> response(200, "MATROSKA") }
        val localPath = server.publish(source("The.Exorcist.1991.mkv"))

        val answer = get(port, localPath).substringBefore("\r\n\r\n")

        assertTrue("statut attendu : $answer", answer.startsWith("HTTP/1.1 200"))
        assertTrue("type attendu : $answer", answer.contains("Content-Type: video/x-matroska"))
        assertTrue("taille attendue : $answer", answer.contains("Content-Length: 8"))
        assertTrue("Range attendu : $answer", answer.contains("Accept-Ranges: bytes"))
        assertTrue("le nom du fichier doit terminer l'URL", localPath.endsWith("/The.Exorcist.1991.mkv"))
        server.stop()
    }

    @Test
    fun `le corps du fichier est transmis sans troncature`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ -> response(200, "MATROSKA") }
        val localPath = server.publish(source("film.mkv"))

        val answer = get(port, localPath)

        assertTrue("corps manquant : $answer", answer.endsWith("MATROSKA"))
        server.stop()
    }

    @Test
    fun `l'en-tete Range du lecteur est transmis a la Freebox`() {
        val server = VlcProxyServer()
        var received: String? = "non transmis"
        val port = server.start { _, range ->
            received = range
            response(206, "MAT")
        }
        val localPath = server.publish(source("film.mkv"))

        val answer = get(port, localPath, range = "bytes=1048576-")

        assertTrue("le proxy doit relayer le 206 : $answer", answer.startsWith("HTTP/1.1 206"))
        assertEquals("bytes=1048576-", received)
        server.stop()
    }

    @Test
    fun `la sonde detecte un flux valide`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ -> response(200, "MATROSKA") }
        val localPath = server.publish(source("film.mkv"))

        assertEquals(200, server.probe(port, localPath))
        server.stop()
    }

    @Test
    fun `un refus de la Freebox remonte son message`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ ->
            throw FreeboxException("Acces refuse au fichier", "access_denied")
        }
        val localPath = server.publish(source("film.mkv"))

        val error = runCatching { server.probe(port, localPath) }.exceptionOrNull()

        assertTrue("erreur attendue, recu $error", error is VlcProxyException)
        assertTrue(
            "message de la box attendu, recu ${error?.message}",
            error?.message?.contains("Acces refuse au fichier") == true,
        )
        server.stop()
    }

    @Test
    fun `un jeton inconnu est refuse`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ -> response(200, "MATROSKA") }

        val answer = get(port, "/v/inconnu/film.mkv").substringBefore("\r\n\r\n")

        assertTrue("404 attendu : $answer", answer.startsWith("HTTP/1.1 404"))
        server.stop()
    }

    /**
     * Un flux `btpreview` peut repondre en chunked, sans `Content-Length` : VLC n'a alors
     * aucune taille de reference. On annonce celle du fichier, connue par la tache.
     */
    @Test
    fun `une taille connue complete un flux sans Content-Length`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ ->
            val body = object : ResponseBody() {
                override fun contentType() = "video/x-matroska".toMediaType()
                override fun contentLength() = -1L
                override fun source(): Buffer = Buffer().apply { writeUtf8("MATROSKA") }
            }
            Response.Builder()
                .request(Request.Builder().url("http://127.0.0.1/upstream").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }
        val localPath = server.publish(source("film.mkv", size = 4_000_000_000L))

        val head = get(port, localPath).substringBefore("\r\n\r\n")

        assertTrue("taille annoncee manquante : $head", head.contains("Content-Length: 4000000000"))
        server.stop()
    }

    /** Une page d'erreur HTML ne doit jamais arriver a VLC sous le type d'une video. */
    @Test
    fun `un type html est remplace par celui du fichier`() {
        val server = VlcProxyServer()
        val port = server.start { _, _ -> response(200, "<html>erreur</html>", contentType = "text/html") }
        val localPath = server.publish(source("film.mkv"))

        val head = get(port, localPath).substringBefore("\r\n\r\n")

        assertTrue("type video attendu : $head", head.contains("Content-Type: video/x-matroska"))
        server.stop()
    }
}
