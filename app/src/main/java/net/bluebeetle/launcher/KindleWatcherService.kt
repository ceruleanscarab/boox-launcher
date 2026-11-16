package net.bluebeetle.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Watches the Kindle app and tells BookStore which book is open, so the home screen can show
 * its cover. Runs happily alongside boox-kindle-cover's own watcher.
 */
class KindleWatcherService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanQueued = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != KINDLE_PACKAGE) return
        // Kindle fires lots of content-changed events; batch them into one scan.
        if (scanQueued) return
        scanQueued = true
        handler.postDelayed({
            scanQueued = false
            scan()
        }, 1200)
    }

    private fun scan() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != KINDLE_PACKAGE) return

        val prefs = Prefs(this)
        val nodes = TitleExtractor.collect(root)
        val info = TitleExtractor.extract(nodes, prefs.titleIdRegex) ?: return
        prefs.lastReadAt = System.currentTimeMillis()
        BookStore.seen(this, info, TitleExtractor.progress(nodes))
    }

    override fun onInterrupt() {}
}
