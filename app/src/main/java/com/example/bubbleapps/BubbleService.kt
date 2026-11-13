package com.example.bubbleapps
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams as LP
import android.widget.*
import kotlin.math.abs

class BubbleService : Service() {
    companion object { var running = false }
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var lp: LP? = null
    private val d get() = resources.displayMetrics.density

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg(); build(); running = true
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("b", "Bubble", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "b").setContentTitle("Bubble is running")
            .setSmallIcon(android.R.drawable.ic_menu_manage).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
    }

    private fun build() {
        closePanel()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        val size = (Prefs.size(this) * d).toInt()
        val v = TextView(this).apply {
            text = "◎"; textSize = Prefs.size(this@BubbleService) / 2.2f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); elevation = 12 * d
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Prefs.color(this@BubbleService)); setStroke((2 * d).toInt(), Color.argb(120, 255, 255, 255)) }
            alpha = Prefs.alpha(this@BubbleService) / 100f
        }
        val p = lp ?: LP(size, size, LP.TYPE_APPLICATION_OVERLAY, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 300 }
        p.width = size; p.height = size; lp = p
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) { p.x = sx + dx.toInt(); p.y = sy + dy.toInt(); wm.updateViewLayout(v, p); closePanel() }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel() else {
                    p.x = if (p.x + size / 2 < resources.displayMetrics.widthPixels / 2) 0 else resources.displayMetrics.widthPixels - size
                    wm.updateViewLayout(v, p)
                }
            }
            true
        }
        wm.addView(v, p); bubble = v
    }

    private fun togglePanel() { if (panel != null) closePanel() else showPanel() }

    private fun closePanel() { panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel = null }

    private fun showPanel() {
        val pm = packageManager
        val row = LinearLayout(this).apply { setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt()) }
        val pkgs = Prefs.apps(this)
        if (pkgs.isEmpty()) row.addView(TextView(this).apply { text = "Open Bubble Apps and choose apps"; setTextColor(Color.WHITE) })
        pkgs.forEach { pkg ->
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { return@forEach }
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0) }
            item.addView(ImageView(this).apply { setImageDrawable(pm.getApplicationIcon(info)); layoutParams = LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt()) })
            item.addView(TextView(this).apply { text = pm.getApplicationLabel(info); textSize = 11f; setTextColor(Color.WHITE); maxLines = 1; maxEms = 6; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER })
            item.setOnClickListener { launch(pkg); closePanel() }
            row.addView(item)
        }
        val card = FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = 28 * d; setColor(Color.argb(235, 28, 28, 40)) }
            elevation = 16 * d
            addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel(); false }
        }
        val b = lp!!
        val pp = LP((resources.displayMetrics.widthPixels - 32 * d).toInt(), LP.WRAP_CONTENT, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = (16 * d).toInt(); y = b.y + b.height + (8 * d).toInt() }
        wm.addView(card, pp); panel = card
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // resumes the existing task: no restart
        val o = ActivityOptions.makeBasic()
        if (Prefs.floating(this)) {
            val w = resources.displayMetrics.widthPixels; val h = resources.displayMetrics.heightPixels
            o.setLaunchBounds(Rect((w * .05).toInt(), (h * .12).toInt(), (w * .95).toInt(), (h * .70).toInt()))
            try { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 5) } catch (_: Throwable) {}
        }
        startActivity(i, o.toBundle())
    }

    override fun onDestroy() {
        closePanel(); bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null; running = false; super.onDestroy()
    }
}
