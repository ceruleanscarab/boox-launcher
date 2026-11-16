package net.bluebeetle.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import java.util.concurrent.Executors

/**
 * The home screen: the book you're reading up top, then widgets, pinned apps, and All apps.
 * Everything is paged rather than scrolled, with no animation, because this is an e-ink screen.
 */
class HomeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var root: FrameLayout
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()

    private var apps: List<AppEntry> = emptyList()
    private var home: View? = null
    private var drawer: View? = null
    private var drawerPage = 0

    private lateinit var coverImage: ImageView
    private lateinit var coverPlaceholder: TextView
    private lateinit var kicker: TextView
    private lateinit var titleView: TextView
    private lateinit var authorView: TextView
    private lateinit var progressRow: LinearLayout
    private lateinit var progressFill: View
    private lateinit var progressRest: View
    private lateinit var progressText: TextView
    private lateinit var lastReadView: TextView
    private lateinit var readButton: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var widgetBox: LinearLayout

    private var widgetSignature: String? = null
    private var coverStamp = -1L
    private var listening = false

    private val landscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, WIDGET_HOST_ID)
        root = FrameLayout(this).apply { setBackgroundColor(PAPER) }
        setContentView(root)
        buildHome()
    }

    override fun onStart() {
        super.onStart()
        try {
            widgetHost.startListening()
            listening = true
        } catch (e: Exception) {
            listening = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (listening) {
            try { widgetHost.stopListening() } catch (e: Exception) { }
            listening = false
        }
    }

    override fun onResume() {
        super.onResume()
        BookStore.onChange = { main.post { refreshBook() } }
        refreshBook()
        refreshWidgets()
        refreshFavorites()
        loadApps()
    }

    override fun onPause() {
        super.onPause()
        BookStore.onChange = null
    }

    /** Pressing Home while already home closes the app list. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Home is the bottom of the stack: Back only ever closes the app list.
        closeDrawer()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val drawerWasOpen = drawer != null
        buildHome()
        refreshBook()
        refreshWidgets()
        refreshFavorites()
        if (drawerWasOpen) showDrawer()
    }

    private fun loadApps() {
        loader.execute {
            val loaded = try { Apps.load(this) } catch (e: Exception) { emptyList() }
            main.post {
                if (isDestroyed) return@post
                val changed = loaded.map { it.key } != apps.map { it.key }
                apps = loaded
                if (changed) {
                    refreshFavorites()
                    if (drawer != null) showDrawer()
                }
            }
        }
    }

    // ---------------------------------------------------------------- home layout

    private fun buildHome() {
        root.removeAllViews()
        drawer = null
        widgetSignature = null
        coverStamp = -1L
        val land = landscape

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextClock(context).apply {
                format12Hour = "EEEE, MMMM d   ·   h:mm a"
                format24Hour = "EEEE, MMMM d   ·   HH:mm"
                textSize = 18f
                setTextColor(INK)
            }, lp(0, WRAP, 1f))
            addView(flatButton("Settings", sizeSp = 15, minHeightDp = 48) {
                startActivity(Intent(context, SettingsActivity::class.java))
            })
        }

        widgetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        favRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val allApps = flatButton("All apps") { showDrawer() }
        val book = buildBookTile(land)

        val content = LinearLayout(this).apply {
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        if (land) {
            content.orientation = LinearLayout.HORIZONTAL
            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(header, lp(MATCH, WRAP))
                addView(widgetBox, lp(MATCH, 0, 1f, top = dp(16)))
                addView(rule(), lp(MATCH, dp(1), top = dp(12)))
                addView(favRow, lp(MATCH, WRAP, top = dp(8)))
                addView(allApps, lp(MATCH, WRAP, top = dp(8)))
            }
            content.addView(book, lp(0, MATCH, 2f))
            content.addView(right, lp(0, MATCH, 3f, left = dp(32)))
        } else {
            content.orientation = LinearLayout.VERTICAL
            content.addView(header, lp(MATCH, WRAP))
            content.addView(book, lp(MATCH, WRAP, top = dp(20)))
            content.addView(rule(), lp(MATCH, dp(1), top = dp(24)))
            content.addView(widgetBox, lp(MATCH, 0, 1f, top = dp(16)))
            content.addView(rule(), lp(MATCH, dp(1), top = dp(12)))
            content.addView(favRow, lp(MATCH, WRAP, top = dp(8)))
            content.addView(allApps, lp(MATCH, WRAP, top = dp(8)))
        }
        home = content
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun buildBookTile(land: Boolean): View {
        val dm = resources.displayMetrics
        val coverW: Int
        val coverH: Int
        if (land) {
            coverH = (dm.heightPixels * 0.5f).toInt()
            coverW = coverH * 2 / 3
        } else {
            coverW = (dm.widthPixels * 0.36f).toInt()
            coverH = coverW * 3 / 2
        }

        coverImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        coverPlaceholder = label("", 16, color = SOFT_INK).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val coverBox = FrameLayout(this).apply {
            background = box(strokeDp = 1, radiusDp = 0)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            addView(coverPlaceholder, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(coverImage, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        kicker = label("", 13, bold = true, color = SOFT_INK).apply { letterSpacing = 0.18f }
        titleView = label("", 30, bold = true, serif = true).apply { maxLines = 4 }
        authorView = label("", 18, color = SOFT_INK).apply { maxLines = 3 }

        progressFill = View(this).apply { setBackgroundColor(INK) }
        progressRest = View(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = box(strokeDp = 1, radiusDp = 0)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            addView(progressFill, lp(0, MATCH, 0f))
            addView(progressRest, lp(0, MATCH, 100f))
        }
        progressText = label("", 16, bold = true)
        progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bar, lp(0, dp(14), 1f))
            addView(progressText, lp(WRAP, WRAP, left = dp(12)))
        }
        lastReadView = label("", 14, color = SOFT_INK).apply { maxLines = 1 }
        readButton = flatButton("", filled = true) { openReader() }

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(kicker, lp(MATCH, WRAP))
            addView(titleView, lp(MATCH, WRAP, top = dp(8)))
            addView(authorView, lp(MATCH, WRAP, top = dp(8)))
            addView(progressRow, lp(MATCH, WRAP, top = dp(20)))
            addView(lastReadView, lp(MATCH, WRAP, top = dp(8)))
            addView(View(context), lp(MATCH, 0, 1f))
            addView(readButton, lp(MATCH, WRAP, top = dp(12)))
        }

        return LinearLayout(this).apply {
            if (land) {
                orientation = LinearLayout.VERTICAL
                addView(coverBox, lp(coverW, coverH))
                addView(text, lp(MATCH, 0, 1f, top = dp(20)))
            } else {
                orientation = LinearLayout.HORIZONTAL
                addView(coverBox, lp(coverW, coverH))
                addView(text, lp(0, MATCH, 1f, left = dp(28)))
            }
            setOnClickListener { openReader() }
        }
    }

    private fun openReader() {
        if (!Apps.launchPackage(this, KINDLE_PACKAGE)) {
            Toast.makeText(this, "The Kindle app isn't installed", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- book tile

    private fun refreshBook() {
        if (home == null) return
        val title = prefs.bookTitle
        if (title == null) {
            kicker.text = "READING"
            titleView.text = "Open a book in Kindle"
            authorView.text = if (isWatcherEnabled()) "Its cover will show up here."
            else "Turn on book detection in Settings to show your current book here."
            progressRow.visibility = View.GONE
            lastReadView.visibility = View.GONE
            readButton.text = "Open Kindle"
        } else {
            kicker.text = "NOW READING"
            titleView.text = title
            val author = prefs.bookAuthor
            authorView.text = author.orEmpty()
            authorView.visibility = if (author.isNullOrBlank()) View.GONE else View.VISIBLE

            val pct = prefs.bookPercent
            if (pct in 0..100) {
                (progressFill.layoutParams as LinearLayout.LayoutParams).weight = pct.toFloat()
                (progressRest.layoutParams as LinearLayout.LayoutParams).weight = (100 - pct).toFloat()
                progressFill.requestLayout()
                progressText.text = "$pct%"
                progressRow.visibility = View.VISIBLE
            } else {
                progressRow.visibility = View.GONE
            }

            val at = prefs.lastReadAt
            if (at > 0) {
                val time = DateFormat.getTimeFormat(this).format(Date(at))
                lastReadView.text = if (DateUtils.isToday(at)) "Last read today at $time"
                else "Last read " + DateFormat.format("EEE, MMM d", at)
                lastReadView.visibility = View.VISIBLE
            } else {
                lastReadView.visibility = View.GONE
            }
            readButton.text = "Continue reading"
        }
        if (title == null) authorView.visibility = View.VISIBLE
        refreshCover(title)
    }

    private fun refreshCover(title: String?) {
        val file = BookStore.coverFile(this)
        val stamp = if (title != null && file.exists()) file.lastModified() else 0L
        coverImage.colorFilter = if (prefs.boostColor) COLOR_BOOST else null
        if (stamp == coverStamp) return
        coverStamp = stamp
        val bmp = if (stamp > 0) BitmapFactory.decodeFile(file.absolutePath) else null
        coverImage.setImageBitmap(bmp)
        coverImage.visibility = if (bmp != null) View.VISIBLE else View.INVISIBLE
        coverPlaceholder.text = if (title == null) "No book yet" else "Looking for the cover…\n\n$title"
    }

    // ---------------------------------------------------------------- widgets

    private fun refreshWidgets() {
        val slots = prefs.widgets
        val signature = slots.joinToString(",") { "${it.id}:${it.heightDp}" }
        if (signature == widgetSignature) return
        widgetSignature = signature
        widgetBox.removeAllViews()

        if (slots.isEmpty()) {
            widgetBox.addView(
                label("No widgets yet. Add a calendar, weather or to-do widget from Settings.", 15, color = SOFT_INK),
                lp(MATCH, WRAP),
            )
            return
        }
        val density = resources.displayMetrics.density
        val widthDp = ((resources.displayMetrics.widthPixels / density).toInt() - 56)
            .let { if (landscape) it * 3 / 5 else it }
        for (slot in slots) {
            val info = widgetManager.getAppWidgetInfo(slot.id) ?: continue
            try {
                val view = widgetHost.createView(this, slot.id, info)
                view.setPadding(0, 0, 0, 0)
                widgetBox.addView(view, lp(MATCH, dp(slot.heightDp), bottom = dp(12)))
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp, slot.heightDp, widthDp, slot.heightDp)
            } catch (e: Exception) {
                // A widget whose app was removed or is misbehaving just doesn't show.
            }
        }
    }

    // ---------------------------------------------------------------- pinned apps

    private fun refreshFavorites() {
        if (home == null) return
        favRow.removeAllViews()
        val byKey = apps.associateBy { it.key }
        val favs = prefs.favorites.mapNotNull { byKey[it] }.take(MAX_FAVORITES)
        if (favs.isEmpty()) {
            val hint = if (apps.isEmpty()) "" else
                "Pin apps here: open All apps, then press and hold one."
            favRow.addView(
                label(hint, 15, color = SOFT_INK).apply { setPadding(0, dp(16), 0, dp(16)) },
                lp(MATCH, WRAP),
            )
            return
        }
        for (app in favs) {
            favRow.addView(appCell(app, iconDp = 72) { favoriteMenu(app) }, lp(0, WRAP, 1f))
        }
    }

    private fun appCell(app: AppEntry, iconDp: Int, onLongPress: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
            addView(ImageView(context).apply {
                setImageDrawable(app.freshIcon())
                if (prefs.boostColor) colorFilter = COLOR_BOOST
            }, lp(dp(iconDp), dp(iconDp)))
            addView(label(app.label, 14).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            }, lp(MATCH, WRAP, top = dp(6)))
            setOnClickListener { Apps.launch(context, app.component) }
            setOnLongClickListener { onLongPress(); true }
        }

    private fun favoriteMenu(app: AppEntry) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Move left", "Move right", "Unpin", "App info")) { _, which ->
                val favs = prefs.favorites.toMutableList()
                val i = favs.indexOf(app.key)
                when (which) {
                    0 -> if (i > 0) { favs.removeAt(i); favs.add(i - 1, app.key) }
                    1 -> if (i in 0 until favs.size - 1) { favs.removeAt(i); favs.add(i + 1, app.key) }
                    2 -> favs.remove(app.key)
                    3 -> Apps.appInfo(this, app.component.packageName)
                }
                prefs.favorites = favs
                refreshFavorites()
            }
            .show()
    }

    // ---------------------------------------------------------------- all apps

    private fun showDrawer() {
        drawer?.let { root.removeView(it) }
        val land = landscape
        val cols = if (land) 7 else 5
        val rows = if (land) 4 else 7
        val perPage = cols * rows
        val hidden = prefs.hidden
        val visible = apps.filter { it.key !in hidden }
        val pages = maxOf(1, (visible.size + perPage - 1) / perPage)
        drawerPage = drawerPage.coerceIn(0, pages - 1)
        val pageApps = visible.drop(drawerPage * perPage).take(perPage)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("All apps", 26, bold = true, serif = true), lp(0, WRAP, 1f))
            addView(label("Page ${drawerPage + 1} of $pages", 16, color = SOFT_INK), lp(WRAP, WRAP, right = dp(20)))
            addView(flatButton("Close", sizeSp = 16, minHeightDp = 56) { closeDrawer() })
        }

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val app = pageApps.getOrNull(r * cols + c)
                val cell = if (app != null) appCell(app, iconDp = 64) { drawerMenu(app) } else View(this)
                row.addView(cell, lp(0, MATCH, 1f))
            }
            grid.addView(row, lp(MATCH, 0, 1f))
        }
        if (visible.isEmpty()) {
            grid.removeAllViews()
            grid.addView(label("Loading apps…", 18, color = SOFT_INK), lp(MATCH, WRAP, top = dp(40)))
        }

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val prev = flatButton("‹  Previous") { drawerPage--; showDrawer() }
            val next = flatButton("Next  ›") { drawerPage++; showDrawer() }
            prev.visibility = if (drawerPage > 0) View.VISIBLE else View.INVISIBLE
            next.visibility = if (drawerPage < pages - 1) View.VISIBLE else View.INVISIBLE
            addView(prev, lp(0, WRAP, 1f))
            addView(next, lp(0, WRAP, 1f, left = dp(16)))
        }

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAPER)
            isClickable = true
            setPadding(dp(28), dp(20), dp(28), dp(20))
            addView(header, lp(MATCH, WRAP))
            addView(rule(), lp(MATCH, dp(1), top = dp(12)))
            addView(grid, lp(MATCH, 0, 1f, top = dp(8)))
            addView(footer, lp(MATCH, WRAP, top = dp(8)))
        }
        home?.visibility = View.INVISIBLE
        drawer = view
        root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun closeDrawer() {
        val view = drawer ?: return
        root.removeView(view)
        drawer = null
        drawerPage = 0
        home?.visibility = View.VISIBLE
    }

    private fun drawerMenu(app: AppEntry) {
        val pinned = app.key in prefs.favorites
        val pinLabel = if (pinned) "Unpin from home" else "Pin to home"
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf(pinLabel, "Hide from this list", "App info")) { _, which ->
                when (which) {
                    0 -> {
                        val favs = prefs.favorites
                        when {
                            pinned -> prefs.favorites = favs - app.key
                            favs.size >= MAX_FAVORITES ->
                                Toast.makeText(this, "Home holds $MAX_FAVORITES pinned apps. Unpin one first.", Toast.LENGTH_LONG).show()
                            else -> prefs.favorites = favs + app.key
                        }
                        refreshFavorites()
                    }
                    1 -> {
                        prefs.hidden = prefs.hidden + app.key
                        showDrawer()
                    }
                    2 -> Apps.appInfo(this, app.component.packageName)
                }
            }
            .show()
    }
}
