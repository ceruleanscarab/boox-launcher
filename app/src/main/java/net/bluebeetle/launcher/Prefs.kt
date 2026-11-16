package net.bluebeetle.launcher

import android.content.Context

const val KINDLE_PACKAGE = "com.amazon.kindle"
const val WIDGET_HOST_ID = 1701
const val MAX_FAVORITES = 6

/** One widget on the home screen: its system id and how tall to draw it. */
data class WidgetSlot(val id: Int, val heightDp: Int)

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Pinned apps, in display order, as flattened component names. */
    var favorites: List<String>
        get() = sp.getString("favorites", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("favorites", v.joinToString("\n")).apply()

    /** Apps left out of the All apps list. */
    var hidden: Set<String>
        get() = sp.getString("hidden", "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()
        set(v) = sp.edit().putString("hidden", v.joinToString("\n")).apply()

    var widgets: List<WidgetSlot>
        get() = sp.getString("widgets", "").orEmpty().split(',').mapNotNull { part ->
            val bits = part.split(':')
            val id = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            WidgetSlot(id, bits.getOrNull(1)?.toIntOrNull() ?: 160)
        }
        set(v) = sp.edit().putString("widgets", v.joinToString(",") { "${it.id}:${it.heightDp}" }).apply()

    var bookTitle: String?
        get() = sp.getString("book_title", null)
        set(v) = sp.edit().putString("book_title", v).apply()

    var bookAuthor: String?
        get() = sp.getString("book_author", null)
        set(v) = sp.edit().putString("book_author", v).apply()

    /** Reading progress 0..100, or -1 when unknown. */
    var bookPercent: Int
        get() = sp.getInt("book_percent", -1)
        set(v) = sp.edit().putInt("book_percent", v).apply()

    var lastReadAt: Long
        get() = sp.getLong("last_read_at", 0L)
        set(v) = sp.edit().putLong("last_read_at", v).apply()

    /** Optional regex matched against Kindle view ids to find the title (overrides the default). */
    var titleIdRegex: String
        get() = sp.getString("title_id_regex", "") ?: ""
        set(v) = sp.edit().putString("title_id_regex", v).apply()

    /** Optional Google Books API key; without one Google often answers HTTP 429. */
    var googleApiKey: String
        get() = sp.getString("google_api_key", "") ?: ""
        set(v) = sp.edit().putString("google_api_key", v).apply()

    /** Push saturation on the cover and icons so Kaleido colour looks less washed out. */
    var boostColor: Boolean
        get() = sp.getBoolean("boost_color", true)
        set(v) = sp.edit().putBoolean("boost_color", v).apply()
}
