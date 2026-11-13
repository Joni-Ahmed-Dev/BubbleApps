package com.example.bubbleapps
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var listView: TextView
    private val d get() = resources.displayMetrics.density

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = (20 * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }
        root.addView(text("Bubble Apps", 28f, true))
        root.addView(text("Add apps, tap the bubble, open them anywhere.", 14f, false))

        root.addView(Button(this).apply {
            text = "Start / Restart Bubble"
            setOnClickListener { start() }
        })
        root.addView(Button(this).apply {
            text = "Stop Bubble"
            setOnClickListener { startService(Intent(context, BubbleService::class.java).setAction("STOP")) }
        })

        root.addView(text("Bubble size", 16f, true))
        root.addView(seek(40, 100, Prefs.size(this)) { Prefs.put(this, "size", it); refresh() })
        root.addView(text("Opacity", 16f, true))
        root.addView(seek(30, 100, Prefs.alpha(this)) { Prefs.put(this, "alpha", it); refresh() })

        root.addView(text("Color", 16f, true))
        val row = LinearLayout(this)
        listOf(0xFF6C63FF, 0xFFFF6584, 0xFF00C896, 0xFFFFB347, 0xFF2D9CDB, 0xFF111111).forEach { c ->
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply { setMargins(0, 8, (12 * d).toInt(), 8) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.toInt()) }
                setOnClickListener { Prefs.put(context, "color", c.toInt()); refresh() }
            })
        }
        root.addView(row)

        root.addView(Switch(this).apply {
            text = "Open apps in floating window (needs freeform support)"
            isChecked = Prefs.floating(context)
            setOnCheckedChangeListener { _, v -> Prefs.putB(context, "floating", v) }
        })

        root.addView(text("Apps in bubble", 16f, true))
        listView = text("", 14f, false); root.addView(listView)
        root.addView(Button(this).apply { text = "Choose apps"; setOnClickListener { pickApps() } })
        setContentView(ScrollView(this).apply { addView(root) })
        updateList()
    }

    private fun text(t: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = t; textSize = size; setPadding(0, (10 * d).toInt(), 0, 4)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun seek(min: Int, max: Int, v: Int, f: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max - min; progress = v - min
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) f(p + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun updateList() {
        val pm = packageManager
        val names = Prefs.apps(this).mapNotNull { try { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() } catch (e: Exception) { null } }
        listView.text = if (names.isEmpty()) "No apps selected" else names.joinToString("\n• ", "• ")
    }

    private fun pickApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val pkgs = apps.map { it.activityInfo.packageName }
        val sel = Prefs.apps(this).toMutableSet()
        AlertDialog.Builder(this).setTitle("Choose apps")
            .setMultiChoiceItems(apps.map { it.loadLabel(pm).toString() }.toTypedArray(),
                BooleanArray(pkgs.size) { pkgs[it] in sel }) { _, i, on -> if (on) sel.add(pkgs[i]) else sel.remove(pkgs[i]) }
            .setPositiveButton("Save") { _, _ -> Prefs.setApps(this, pkgs.filter { it in sel }); updateList(); refresh() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, BubbleService::class.java))
    }

    private fun refresh() { if (BubbleService.running) startForegroundService(Intent(this, BubbleService::class.java)) }
}
