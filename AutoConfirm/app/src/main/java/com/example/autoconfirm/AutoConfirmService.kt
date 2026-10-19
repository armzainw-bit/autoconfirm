package com.example.autoconfirm

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoConfirmService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastClick = 0L
    private var lastScan = 0L
    private var lastSeenPkg = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()

        if (pkg != packageName && pkg != "com.android.systemui") {
            if (pkg != lastSeenPkg) {
                lastSeenPkg = pkg
                p.edit().putString("last_pkg", pkg).apply()
            }
            // เก็บข้อความของปุ่มที่เห็นบนหน้าจอ ไว้ให้เลือกในหน้าตั้งค่า (เก็บในเครื่องเท่านั้น)
            if (now - lastScan > 1500) {
                lastScan = now
                rootInActiveWindow?.let { r ->
                    val out = linkedSetOf<String>()
                    collect(r, out, 0)
                    if (out.isNotEmpty()) p.edit().putString("seen", JSONArray(out.toList()).toString()).apply()
                }
            }
        }

        if (!p.getBoolean("enabled", false)) return
        val target = p.getString("target", "")!!.trim()
        if (target.isEmpty() || pkg != target) return
        if (now - lastClick < p.getLong("cooldown", 5000)) return

        val labels = activeLabels(p.getString("labels", null))
        if (labels.isEmpty()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != target) return

        var node: AccessibilityNodeInfo? = null
        var hit = ""
        for (l in labels) {
            node = root.findAccessibilityNodeInfosByText(l).firstOrNull {
                it.text?.toString()?.trim() == l || it.contentDescription?.toString()?.trim() == l
            }
            if (node != null) { hit = l; break }
        }
        var c: AccessibilityNodeInfo? = node ?: return
        while (c != null && !c.isClickable) c = c.parent
        val btn = c ?: return

        lastClick = now
        val delay = p.getLong("delay", 500)
        handler.postDelayed({
            val ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            log("กด \"$hit\" ในแอป $target -> ${if (ok) "สำเร็จ" else "ไม่สำเร็จ"}")
        }, delay)
    }

    private fun activeLabels(json: String?): List<String> {
        if (json == null) return listOf("ยืนยันการจ่าย")
        val a = JSONArray(json)
        val res = mutableListOf<String>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getBoolean("on") && o.getString("t").isNotBlank()) res.add(o.getString("t").trim())
        }
        return res
    }

    private fun collect(n: AccessibilityNodeInfo, out: MutableSet<String>, depth: Int) {
        if (depth > 25 || out.size >= 40) return
        if (n.isClickable) {
            val t = (n.text ?: n.contentDescription)?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length <= 30) out.add(t)
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { collect(it, out, depth + 1) }
    }

    private fun log(msg: String) {
        val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        try { File(filesDir, "log.txt").appendText("$t  $msg\n") } catch (_: Exception) {}
    }

    override fun onInterrupt() {}
}
