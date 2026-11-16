package net.bluebeetle.launcher

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.Executors

/** Remembers the current book and keeps its cover image on disk for the home screen. */
object BookStore {

    private const val MAX_COVER_WIDTH = 900
    private val exec = Executors.newSingleThreadExecutor()

    /** Title we already tried a lookup for, so a failed lookup isn't repeated on every scan. */
    private var attempted: String? = null

    /** Called (on any thread) whenever the book or its cover changes. */
    @Volatile
    var onChange: (() -> Unit)? = null

    /** What the last cover lookup did, shown in settings. */
    @Volatile
    var status: String = ""

    fun coverFile(ctx: Context) = File(ctx.applicationContext.filesDir, "cover.jpg")

    /** The watcher saw this book open in Kindle. */
    @Synchronized
    fun seen(context: Context, info: BookInfo, percent: Int?) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        var changed = false

        if (!info.title.equals(prefs.bookTitle, ignoreCase = true)) {
            prefs.bookTitle = info.title
            prefs.bookAuthor = info.author
            prefs.bookPercent = percent ?: -1
            coverFile(ctx).delete()
            attempted = null
            changed = true
        } else {
            if (info.author != null && prefs.bookAuthor == null) {
                prefs.bookAuthor = info.author
                changed = true
            }
            if (percent != null && percent != prefs.bookPercent) {
                prefs.bookPercent = percent
                changed = true
            }
        }

        if (!coverFile(ctx).exists() && attempted != info.title) {
            attempted = info.title
            fetch(ctx, BookInfo(info.title, prefs.bookAuthor))
        }
        if (changed) onChange?.invoke()
    }

    /** Set the book by hand from settings, or retry the lookup for the current one. */
    @Synchronized
    fun setManual(context: Context, title: String, author: String?) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        if (!title.equals(prefs.bookTitle, ignoreCase = true)) prefs.bookPercent = -1
        prefs.bookTitle = title
        prefs.bookAuthor = author
        coverFile(ctx).delete()
        attempted = title
        fetch(ctx, BookInfo(title, author))
        onChange?.invoke()
    }

    @Synchronized
    fun forget(context: Context) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        prefs.bookTitle = null
        prefs.bookAuthor = null
        prefs.bookPercent = -1
        prefs.lastReadAt = 0L
        coverFile(ctx).delete()
        attempted = null
        status = ""
        onChange?.invoke()
    }

    private fun fetch(ctx: Context, info: BookInfo) {
        status = "Looking up a cover for \"${info.title}\"…"
        exec.execute {
            val lines = ArrayList<String>()
            val bmp = try {
                CoverFetcher.fetch(info, Prefs(ctx).googleApiKey) { lines.add(it) }
            } catch (e: Exception) {
                lines.add("Lookup error: ${e.message}")
                null
            }
            // The book may have changed while we were looking.
            if (!info.title.equals(Prefs(ctx).bookTitle, ignoreCase = true)) return@execute
            if (bmp != null) {
                try {
                    save(ctx, bmp)
                } catch (e: Exception) {
                    lines.add("Couldn't save the cover: ${e.message}")
                }
            } else {
                lines.add("No cover found for \"${info.title}\".")
            }
            status = lines.lastOrNull().orEmpty()
            onChange?.invoke()
        }
    }

    private fun save(ctx: Context, src: Bitmap) {
        val scaled = if (src.width > MAX_COVER_WIDTH) {
            val h = (src.height.toLong() * MAX_COVER_WIDTH / src.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, MAX_COVER_WIDTH, h, true)
        } else src
        val target = coverFile(ctx)
        val tmp = File(target.parentFile, "cover.tmp")
        tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
