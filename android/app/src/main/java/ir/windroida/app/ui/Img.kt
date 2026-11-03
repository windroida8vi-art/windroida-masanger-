package ir.windroida.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import ir.windroida.app.net.Http
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Loads pictures from the server (avatars, thumbnails, full images) into memory/disk caches. Callbacks run on the UI thread. */
class Img(private val http: Http, private val dir: File) {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(48 * 1024 * 1024)) {
        override fun sizeOf(k: String, v: Bitmap): Int = v.byteCount
    }

    init { dir.mkdirs(); trim() }

    private fun trim() {
        pool.execute {
            val fs = dir.listFiles() ?: return@execute
            if (fs.size > 400) fs.sortedBy { it.lastModified() }.take(fs.size - 300).forEach { it.delete() }
        }
    }

    private fun key(path: String): String = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }

    /** [path] is the server path WITHOUT the media key; [withKey] adds it. [maxPx] limits the decoded size. */
    fun load(path: String, mediaKey: String, maxPx: Int, cb: (Bitmap?) -> Unit) {
        val id = "$path@$maxPx"
        val hit = mem.get(id)
        if (hit != null) { cb(hit); return }
        pool.execute {
            var bmp: Bitmap? = null
            try {
                val f = File(dir, key(path))
                val bytes: ByteArray = if (f.isFile && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); f.readBytes() }
                else {
                    val sep = if (path.contains('?')) "&" else "?"
                    val b = http.get(path + sep + "k=" + java.net.URLEncoder.encode(mediaKey, "UTF-8"), 16 * 1024 * 1024)
                    try { f.writeBytes(b) } catch (_: Exception) {}
                    b
                }
                bmp = decode(bytes, maxPx)
            } catch (_: Exception) {}
            if (bmp != null) mem.put(id, bmp)
            main.post { cb(bmp) }
        }
    }

    fun forget(path: String) { try { File(dir, key(path)).delete() } catch (_: Exception) {} }

    companion object {
        fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0) return null
            var s = 1
            while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
        }
    }
}
