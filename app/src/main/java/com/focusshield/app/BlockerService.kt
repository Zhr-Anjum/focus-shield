package com.focusshield.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class BlockerService : AccessibilityService() {

    private lateinit var store: Store
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var pill: LinearLayout? = null
    private var pillText: TextView? = null
    private var currentPkg: String? = null
    private var tickPkg: String = ""
    private var tickUntil: Long = 0L
    private var lastLaunch = 0L

    private val tick = object : Runnable {
        override fun run() {
            val left = tickUntil - System.currentTimeMillis()
            if (left <= 0) {
                val p = tickPkg
                hidePill()
                store.setUntil(p, 0L)
                if (currentPkg == p) block(p, null)
                return
            }
            val s = left / 1000
            pillText?.text = String.format("%02d:%02d", s / 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = Store(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == "com.android.systemui" || isKeyboard(pkg)) return
        currentPkg = pkg
        if (pkg !in store.locked) {
            hidePill()
            return
        }
        evaluate(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hidePill()
        super.onDestroy()
    }

    private fun isKeyboard(pkg: String): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private fun evaluate(pkg: String) {
        val s = store
        val mins = nowMinutes()

        if (s.sleepOn && inWindow(s.sleepStart, s.sleepEnd, mins)) {
            block(pkg, "Sleep time. Locked until ${fmtTime(s.sleepEnd)}.")
            return
        }
        if (pkg in s.trading) {
            if (s.tradeOn && inWindow(s.tradeStart, s.tradeEnd, mins)) {
                hidePill()
                return
            }
            val m = if (s.tradeOn)
                "Outside your trading window (${fmtTime(s.tradeStart)} to ${fmtTime(s.tradeEnd)})."
            else
                "Trading app locked. Set a trading window in the Schedule tab."
            block(pkg, m)
            return
        }
        val until = s.until(pkg)
        if (until > System.currentTimeMillis()) showPill(pkg, until) else block(pkg, null)
    }

    private fun block(pkg: String, msg: String?) {
        hidePill()
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 1200) return
        lastLaunch = now
        val i = Intent(this, LockActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            .putExtra("pkg", pkg)
            .putExtra("msg", msg)
        startActivity(i)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showPill(pkg: String, until: Long) {
        ensurePill()
        tickPkg = pkg
        tickUntil = until
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun ensurePill() {
        if (pill != null) return
        val time = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            text = "--:--"
        }
        val donate = TextView(this).apply {
            text = "  \u2665 Donate"
            textSize = 13f
            setTextColor(0xFFFFB4C8.toInt())
            setOnClickListener {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(i) } catch (e: Exception) { }
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xE6222222.toInt())
                cornerRadius = dp(24).toFloat()
            }
            addView(time)
            addView(donate)
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        try {
            wm.addView(box, lp)
            pill = box
            pillText = time
        } catch (e: Exception) {
            pill = null
            pillText = null
        }
    }

    private fun hidePill() {
        handler.removeCallbacks(tick)
        val v = pill
        if (v != null) {
            try { wm.removeView(v) } catch (e: Exception) { }
        }
        pill = null
        pillText = null
    }
}
