package ir.windroida.app.net

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/** HTTP(S) to the Windroida server with the pinned certificate (media, avatars, uploads). Blocking: call from a background thread. */
class Http(private val cfg: ServerCfg) {
    private val ctx: SSLContext? = if (cfg.tls && cfg.pin.isNotEmpty()) SSLContext.getInstance("TLS").also { it.init(null, arrayOf<TrustManager>(PinTrust(cfg.pin)), null) } else null

    fun q(vararg kv: Pair<String, Any?>): String =
        kv.filter { it.second != null }.joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second.toString(), "UTF-8") }

    fun url(path: String): String = cfg.base + path

    private fun open(path: String, method: String): HttpURLConnection {
        if (cfg.tls && ctx == null) throw IOException("certificate not confirmed yet")
        val c = URL(url(path)).openConnection() as HttpURLConnection
        if (c is HttpsURLConnection) {
            c.sslSocketFactory = ctx!!.socketFactory
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }      // identity = the pinned fingerprint
        }
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 30000
        c.instanceFollowRedirects = false
        return c
    }

    fun get(path: String, maxBytes: Int = 32 * 1024 * 1024): ByteArray {
        val c = open(path, "GET")
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val len = c.contentLength
            if (len > maxBytes) throw IOException("too big")
            return c.inputStream.use { readAll(it, maxBytes) }
        } finally { c.disconnect() }
    }

    /** Streams the answer into [dest] (written to a temp name first, then renamed) and reports (done, total). */
    fun download(path: String, dest: File, progress: ((Long, Long) -> Unit)? = null, cancelled: () -> Boolean = { false }) {
        val tmp = File(dest.path + ".part")
        val c = open(path, "GET")
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val total = c.contentLengthLong
            c.inputStream.use { ins ->
                FileOutputStream(tmp).use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (cancelled()) throw IOException("cancelled")
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n); done += n
                        progress?.invoke(done, total)
                    }
                    if (total >= 0 && done != total) throw IOException("incomplete download")
                }
            }
            if (!tmp.renameTo(dest)) throw IOException("cannot save")
        } catch (e: Exception) {
            tmp.delete(); throw e
        } finally { c.disconnect() }
    }

    /** POST a raw body (the file) to /upload?...; returns the parsed JSON answer. [length] must be exact. */
    fun upload(path: String, body: InputStream, length: Long, progress: ((Long, Long) -> Unit)? = null, cancelled: () -> Boolean = { false }): Map<String, Any?> {
        val c = open(path, "POST")
        try {
            c.doOutput = true
            c.readTimeout = 120000
            c.setFixedLengthStreamingMode(length)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { os ->
                val buf = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    if (cancelled()) throw IOException("cancelled")
                    val n = body.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n); sent += n
                    progress?.invoke(sent, length)
                }
                if (sent != length) throw IOException("file changed while uploading")
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { String(readAll(it, 1 shl 20), Charsets.UTF_8) } ?: ""
            val j = Json.obj(text) ?: throw IOException("bad answer (HTTP $code)")
            if (!j.bool("ok")) throw ApiError(j.str("error", "ارسال ناموفق بود"), true)
            return j
        } finally { c.disconnect() }
    }

    private fun readAll(ins: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("too big")
        }
        return out.toByteArray()
    }
}
