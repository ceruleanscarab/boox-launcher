package net.bluebeetle.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Setup and housekeeping: default home, book detection, widgets, pinned and hidden apps. */
class SettingsActivity : Activity() {

    companion object {
        private const val REQ_BIND = 1
        private const val REQ_CONFIGURE = 2
        private const val REQ_ROLE = 3
        private const val NO_WIDGET = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private lateinit var prefs: Prefs
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var scroll: ScrollView
    private val main = Handler(Looper.getMainLooper())
    private var pendingWidget = NO_WIDGET

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, WIDGET_HOST_ID)
        pendingWidget = savedInstanceState?.getInt("pendingWidget", NO_WIDGET) ?: NO_WIDGET
        scroll = ScrollView(this).apply { setBackgroundColor(PAPER) }
        setContentView(scroll)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingWidget", pendingWidget)
    }

    override fun onResume() {
        super.onResume()
        // A background cover lookup shouldn't rebuild the screen out from under someone typing.
        BookStore.onChange = { main.post { if (currentFocus !is EditText) render() } }
        render()
    }

    override fun onPause() {
        super.onPause()
        BookStore.onChange = null
    }

    // ---------------------------------------------------------------- screen

    private fun render() {
        if (isDestroyed) return
        val keepScroll = scroll.scrollY
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(40))
            // Takes focus when a text box gives it up, so the keyboard doesn't pop back.
            isFocusable = true
            isFocusableInTouchMode = true
        }

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Beetle Home settings", 26, bold = true, serif = true), lp(0, WRAP, 1f))
            addView(flatButton("Done", sizeSp = 16, minHeightDp = 56) { finish() })
        })

        homeSection(page)
        bookSection(page)
        widgetSection(page)
        pinnedSection(page)
        hiddenSection(page)

        scroll.removeAllViews()
        scroll.addView(page)
        scroll.post { scroll.scrollTo(0, keepScroll) }
    }

    private fun homeSection(page: LinearLayout) {
        page.addView(heading("Home screen"))
        val isDefault = packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
        page.addView(body(
            if (isDefault) "Beetle Home is your home screen."
            else "Beetle Home isn't your default home screen yet. Choose it as the Home app, then press Home."
        ))
        page.addView(flatButton("Choose home app") { chooseHome() }, lp(MATCH, WRAP, top = dp(10)))
        page.addView(check("Boost colour on the cover and app icons", prefs.boostColor) { prefs.boostColor = it })
    }

    private fun chooseHome() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            return
        } catch (e: Exception) {
            // Boox hides some stock settings screens; fall through to the role request.
        }
        try {
            val roles = getSystemService(RoleManager::class.java)
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE)
        } catch (e: Exception) {
            toast("This firmware doesn't offer a home-app chooser. See the README for the adb command.")
        }
    }

    private fun bookSection(page: LinearLayout) {
        page.addView(heading("Current book"))
        val on = isWatcherEnabled()
        page.addView(body(
            if (on) "Book detection is on. Open a book in Kindle and tap the middle of the page once so the title bar shows."
            else "Book detection is off. Turn on Beetle Home under Accessibility so it can read the title of the book open in Kindle."
        ))
        page.addView(flatButton("Open accessibility settings") {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (e: Exception) { toast("Couldn't open accessibility settings") }
        }, lp(MATCH, WRAP, top = dp(10)))

        val title = prefs.bookTitle
        page.addView(body(
            if (title == null) "No book detected yet."
            else "Showing: $title" + (prefs.bookAuthor?.let { " by $it" } ?: "")
        ).apply { setPadding(0, dp(14), 0, 0) })
        if (BookStore.status.isNotEmpty()) page.addView(body(BookStore.status, soft = true))

        page.addView(body("Set the book by hand (also retries the cover lookup):").apply { setPadding(0, dp(14), 0, 0) })
        val titleIn = input("Title", title.orEmpty())
        val authorIn = input("Author (optional)", prefs.bookAuthor.orEmpty())
        page.addView(titleIn, lp(MATCH, WRAP, top = dp(6)))
        page.addView(authorIn, lp(MATCH, WRAP, top = dp(6)))
        page.addView(row(
            flatButton("Find cover", sizeSp = 16) {
                val t = titleIn.text.toString().trim()
                if (t.isEmpty()) toast("Enter a title first")
                else {
                    clearTyping()
                    BookStore.setManual(this, t, authorIn.text.toString().trim().ifEmpty { null })
                }
            },
            flatButton("Forget book", sizeSp = 16) {
                clearTyping()
                BookStore.forget(this)
            },
        ), lp(MATCH, WRAP, top = dp(10)))

        page.addView(body("Advanced", soft = true).apply { setPadding(0, dp(18), 0, 0) })
        val regexIn = input("Title view-id regex (blank = built-in)", prefs.titleIdRegex)
        val keyIn = input("Google Books API key (optional)", prefs.googleApiKey)
        page.addView(regexIn, lp(MATCH, WRAP, top = dp(6)))
        page.addView(keyIn, lp(MATCH, WRAP, top = dp(6)))
        page.addView(flatButton("Save advanced settings", sizeSp = 16) {
            prefs.titleIdRegex = regexIn.text.toString().trim()
            prefs.googleApiKey = keyIn.text.toString().trim()
            clearTyping()
            toast("Saved")
            render()
        }, lp(MATCH, WRAP, top = dp(10)))
    }

    private fun widgetSection(page: LinearLayout) {
        page.addView(heading("Widgets"))
        val slots = prefs.widgets
        if (slots.isEmpty()) page.addView(body("No widgets on the home screen yet."))
        slots.forEachIndexed { i, slot ->
            val info = widgetManager.getAppWidgetInfo(slot.id)
            val name = info?.loadLabel(packageManager) ?: "Unavailable widget"
            page.addView(body("$name  ·  ${slot.heightDp} dp tall").apply { setPadding(0, dp(12), 0, 0) })
            page.addView(row(
                small("Up") { if (i > 0) prefs.widgets = slots.toMutableList().apply { add(i - 1, removeAt(i)) } },
                small("Shorter") { resize(slot, -40) },
                small("Taller") { resize(slot, 40) },
                small("Remove") {
                    try { widgetHost.deleteAppWidgetId(slot.id) } catch (e: Exception) { }
                    prefs.widgets = slots.filter { it.id != slot.id }
                },
            ), lp(MATCH, WRAP, top = dp(6)))
        }
        page.addView(flatButton("Add a widget") { pickWidget() }, lp(MATCH, WRAP, top = dp(14)))
    }

    private fun resize(slot: WidgetSlot, delta: Int) {
        val h = (slot.heightDp + delta).coerceIn(80, 900)
        prefs.widgets = prefs.widgets.map { if (it.id == slot.id) it.copy(heightDp = h) else it }
    }

    private fun pinnedSection(page: LinearLayout) {
        page.addView(heading("Pinned apps"))
        val favs = prefs.favorites
        if (favs.isEmpty()) {
            page.addView(body("None yet. In All apps, press and hold an app and choose Pin to home."))
            return
        }
        favs.forEachIndexed { i, key ->
            page.addView(body(appLabel(key)).apply { setPadding(0, dp(12), 0, 0) })
            page.addView(row(
                small("Earlier") { if (i > 0) prefs.favorites = favs.toMutableList().apply { add(i - 1, removeAt(i)) } },
                small("Later") { if (i < favs.size - 1) prefs.favorites = favs.toMutableList().apply { add(i + 1, removeAt(i)) } },
                small("Unpin") { prefs.favorites = favs - key },
            ), lp(MATCH, WRAP, top = dp(6)))
        }
    }

    private fun hiddenSection(page: LinearLayout) {
        val hidden = prefs.hidden
        if (hidden.isEmpty()) return
        page.addView(heading("Hidden apps"))
        for (key in hidden.sortedBy { appLabel(it).lowercase() }) {
            page.addView(row(
                body(appLabel(key)),
                small("Show") { prefs.hidden = prefs.hidden - key },
            ), lp(MATCH, WRAP, top = dp(8)))
        }
    }

    private fun appLabel(key: String): String {
        val pkg = key.substringBefore('/')
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    // ---------------------------------------------------------------- adding a widget

    private fun pickWidget() {
        val providers = widgetManager.installedProviders
            .sortedBy { it.loadLabel(packageManager).lowercase() }
        if (providers.isEmpty()) {
            toast("No apps on this device offer widgets")
            return
        }
        val names = providers.map { "${it.loadLabel(packageManager)}  —  ${appLabel(it.provider.packageName)}" }
        AlertDialog.Builder(this)
            .setTitle("Add a widget")
            .setItems(names.toTypedArray()) { _, which -> bind(providers[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bind(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configure(id)
            return
        }
        // First time only: Android asks whether this launcher may host widgets.
        try {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                REQ_BIND,
            )
        } catch (e: Exception) {
            cancelPending()
            toast("This firmware won't show the widget permission prompt. See the README for the adb command.")
        }
    }

    private fun configure(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info == null) {
            cancelPending()
            toast("Couldn't add that widget")
            return
        }
        if (info.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // No usable setup screen; add it as-is.
            }
        }
        finishAdd(id, info)
    }

    private fun finishAdd(id: Int, info: AppWidgetProviderInfo?) {
        val density = resources.displayMetrics.density
        val wanted = ((info?.minHeight ?: 0) / density).toInt()
        prefs.widgets = prefs.widgets + WidgetSlot(id, wanted.coerceIn(120, 600))
        pendingWidget = NO_WIDGET
        toast("Widget added")
        render()
    }

    private fun cancelPending() {
        if (pendingWidget != NO_WIDGET) {
            try { widgetHost.deleteAppWidgetId(pendingWidget) } catch (e: Exception) { }
            pendingWidget = NO_WIDGET
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = pendingWidget
        when (requestCode) {
            REQ_BIND ->
                if (resultCode == RESULT_OK && id != NO_WIDGET) configure(id) else cancelPending()
            REQ_CONFIGURE ->
                if (resultCode == RESULT_OK && id != NO_WIDGET) finishAdd(id, widgetManager.getAppWidgetInfo(id))
                else cancelPending()
        }
    }

    // ---------------------------------------------------------------- small view helpers

    private fun heading(text: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(30), 0, dp(8))
        addView(label(text, 20, bold = true))
        addView(rule(), lp(MATCH, dp(1), top = dp(6)))
    }

    private fun body(text: String, soft: Boolean = false): TextView =
        label(text, 16, color = if (soft) SOFT_INK else INK).apply { ellipsize = null }

    /** A list-row button: applies the change, then redraws the screen. */
    private fun small(text: String, action: () -> Unit): TextView =
        flatButton(text, sizeSp = 15, minHeightDp = 56) {
            action()
            render()
        }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v -> addView(v, lp(0, WRAP, 1f, left = if (i == 0) 0 else dp(10))) }
    }

    private fun input(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(true)
        setTextColor(INK)
        textSize = 17f
        background = box(strokeDp = 1, radiusDp = 4)
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun check(text: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            this.text = text
            isChecked = checked
            textSize = 16f
            setTextColor(INK)
            setPadding(dp(8), dp(16), 0, dp(16))
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

    /** Put the keyboard away and drop focus so background redraws resume. */
    private fun clearTyping() {
        val focused = currentFocus ?: return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
