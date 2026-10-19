package com.example.autoconfirm

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    data class Lbl(var t: String, var on: Boolean)

    private lateinit var swEnabled: Switch
    private lateinit var etTarget: EditText
    private lateinit var etDelay: EditText
    private lateinit var etCooldown: EditText
    private lateinit var tvLast: TextView
    private lateinit var tvLog: TextView
    private lateinit var labelBox: LinearLayout
    private lateinit var seenBox: LinearLayout

    private val prefs get() = getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun loadLabels(): MutableList<Lbl> {
        val s = prefs.getString("labels", null) ?: return mutableListOf(Lbl("ยืนยันการจ่าย", true))
        val a = JSONArray(s)
        return MutableList(a.length()) { i -> a.getJSONObject(i).let { Lbl(it.getString("t"), it.getBoolean("on")) } }
    }

    private fun saveLabels(l: List<Lbl>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.t).put("on", it.on)) }
        prefs.edit().putString("labels", a.toString()).apply()
    }

    private fun loadSeen(): List<String> {
        val s = prefs.getString("seen", null) ?: return emptyList()
        val a = JSONArray(s)
        return List(a.length()) { a.getString(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        setContentView(ScrollView(this).apply { addView(box) })

        fun title(t: String) = box.addView(TextView(this).apply {
            text = t; textSize = 16f; setPadding(0, pad, 0, pad / 3)
        })
        fun edit(num: Boolean = false) = EditText(this).apply {
            if (num) inputType = InputType.TYPE_CLASS_NUMBER
            box.addView(this)
        }

        box.addView(Button(this).apply {
            text = "1) เปิดบริการ Accessibility (เลือก Auto Confirm แล้วกดเปิด)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        swEnabled = Switch(this).apply { text = "เปิดกดอัตโนมัติ"; setPadding(0, pad, 0, pad) }
        box.addView(swEnabled)

        title("แอปเป้าหมาย (package name) — กดเฉพาะแอปนี้")
        etTarget = edit()
        tvLast = TextView(this); box.addView(tvLast)
        box.addView(Button(this).apply {
            text = "ใช้แอปล่าสุดที่เปิดอยู่ (เปิดแอปจ่ายเงินก่อน แล้วสลับกลับมา)"
            setOnClickListener { prefs.getString("last_pkg", null)?.let { etTarget.setText(it) } }
        })

        title("คำบนปุ่มที่ให้กด (ติ๊กเลือกใช้ได้หลายคำ)")
        labelBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(labelBox)
        box.addView(Button(this).apply {
            text = "+ เพิ่มคำใหม่"
            setOnClickListener {
                showEdit("") { t ->
                    val l = loadLabels(); l.add(Lbl(t, true)); saveLabels(l); render()
                }
            }
        })

        title("คำที่พบบนหน้าจอแอปล่าสุด (แตะเพื่อเพิ่มเข้ารายการด้านบน)")
        seenBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(seenBox)
        box.addView(Button(this).apply {
            text = "รีเฟรชคำที่พบ"
            setOnClickListener { render() }
        })

        title("หน่วงเวลาก่อนกด (มิลลิวินาที)")
        etDelay = edit(true)
        title("กันกดซ้ำ (มิลลิวินาที)")
        etCooldown = edit(true)

        box.addView(Button(this).apply {
            text = "บันทึกการตั้งค่า"
            setOnClickListener { save() }
        })

        title("บันทึกการกดล่าสุด")
        tvLog = TextView(this); box.addView(tvLog)
        box.addView(Button(this).apply { text = "รีเฟรช log"; setOnClickListener { render() } })
        box.addView(Button(this).apply {
            text = "ล้าง log"
            setOnClickListener { File(filesDir, "log.txt").delete(); render() }
        })

        etTarget.setText(prefs.getString("target", ""))
        etDelay.setText(prefs.getLong("delay", 500).toString())
        etCooldown.setText(prefs.getLong("cooldown", 5000).toString())
        swEnabled.isChecked = prefs.getBoolean("enabled", false)
        swEnabled.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("enabled", on).apply()
            Toast.makeText(this, if (on) "เปิดแล้ว" else "ปิดแล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() { super.onResume(); render() }

    private fun showEdit(initial: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("พิมพ์คำบนปุ่ม (ต้องตรงทุกตัวอักษร)")
            .setView(et)
            .setPositiveButton("ตกลง") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotEmpty()) onOk(t)
            }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun render() {
        // รายการคำที่ใช้กด
        val labels = loadLabels()
        labelBox.removeAllViews()
        labels.forEachIndexed { i, l ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(CheckBox(this).apply {
                text = l.t; isChecked = l.on
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, c -> l.on = c; saveLabels(labels) }
            })
            row.addView(Button(this).apply {
                text = "แก้ไข"
                setOnClickListener { showEdit(l.t) { t -> l.t = t; saveLabels(labels); render() } }
            })
            row.addView(Button(this).apply {
                text = "ลบ"
                setOnClickListener { labels.removeAt(i); saveLabels(labels); render() }
            })
            labelBox.addView(row)
        }
        if (labels.isEmpty()) labelBox.addView(TextView(this).apply { text = "(ยังไม่มีคำ กดเพิ่มคำใหม่)" })

        // คำที่พบบนหน้าจอ
        seenBox.removeAllViews()
        val seen = loadSeen().filter { s -> labels.none { it.t == s } }
        if (seen.isEmpty()) {
            seenBox.addView(TextView(this).apply { text = "(ยังไม่พบ — เปิดบริการ Accessibility แล้วเปิดแอปจ่ายเงินให้เห็นปุ่ม จากนั้นสลับกลับมากดรีเฟรช)" })
        }
        seen.forEach { s ->
            seenBox.addView(Button(this).apply {
                text = "+ $s"
                setOnClickListener {
                    val l = loadLabels(); l.add(Lbl(s, true)); saveLabels(l); render()
                }
            })
        }

        tvLast.text = "แอปล่าสุดที่ระบบเห็น: " + (prefs.getString("last_pkg", "-"))
        val f = File(filesDir, "log.txt")
        tvLog.text = if (f.exists()) f.readLines().takeLast(30).joinToString("\n") else "(ยังไม่มี)"
    }

    private fun save() {
        prefs.edit()
            .putString("target", etTarget.text.toString().trim())
            .putLong("delay", etDelay.text.toString().toLongOrNull() ?: 500)
            .putLong("cooldown", etCooldown.text.toString().toLongOrNull() ?: 5000)
            .apply()
        Toast.makeText(this, "บันทึกแล้ว", Toast.LENGTH_SHORT).show()
    }
}
