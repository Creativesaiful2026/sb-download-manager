package com.mydl.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo

class DownloaderActivity : Activity() {
    private class Choice(val label: String, val fmt: String?, val audio: String?)

    private lateinit var urlBox: EditText
    private lateinit var extraBox: EditText
    private lateinit var status: TextView
    private lateinit var spin: ProgressBar
    private lateinit var info: TextView
    private lateinit var group: RadioGroup
    private lateinit var startBtn: Button
    private var title = "ভিডিও"
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        col.addView(TextView(this).apply { text = "ভিডিও / অডিও ডাউনলোডার"; textSize = 20f })
        urlBox = EditText(this).apply { hint = "ভিডিওর লিংক পেস্ট করুন"; setSingleLine() }
        col.addView(urlBox)
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "পেস্ট"; setOnClickListener { paste() } })
        row.addView(Button(this).apply { text = "তথ্য আনুন"; setOnClickListener { fetch() } })
        col.addView(row)
        spin = ProgressBar(this).apply { visibility = View.GONE }
        col.addView(spin)
        status = TextView(this); col.addView(status)
        info = TextView(this).apply { textSize = 16f }; col.addView(info)
        group = RadioGroup(this); col.addView(group)
        startBtn = Button(this).apply { text = "⬇ ডাউনলোড শুরু করুন"; visibility = View.GONE; setOnClickListener { start() } }
        col.addView(startBtn)

        col.addView(TextView(this).apply { text = "\nঅ্যাডভান্সড: অতিরিক্ত yt-dlp অপশন (ঐচ্ছিক)" })
        extraBox = EditText(this).apply { hint = "যেমন: --extractor-args youtube:player_client=android_vr"; setText(prefs.getString("extra", "")) }
        col.addView(extraBox)
        col.addView(Button(this).apply { text = "ইঞ্জিন আপডেট"; setOnClickListener { updateEngine() } })

        setContentView(ScrollView(this).apply { addView(col); isFillViewport = true })

        val u = intent.getStringExtra("url")
        if (u != null) { urlBox.setText(u); fetch() }
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val u = Util.firstUrl(cm.primaryClip?.getItemAt(0)?.text?.toString())
        if (u != null) { urlBox.setText(u); fetch() } else toast("ক্লিপবোর্ডে লিংক নেই")
    }

    private fun busy(msg: String?) {
        runOnUiThread { spin.visibility = if (msg != null) View.VISIBLE else View.GONE; status.text = msg ?: "" }
    }

    private fun fetch() {
        val url = Util.firstUrl(urlBox.text.toString()) ?: return toast("সঠিক লিংক দিন")
        prefs.edit().putString("extra", extraBox.text.toString()).apply()
        group.removeAllViews(); info.text = ""; startBtn.visibility = View.GONE
        busy("তথ্য আনা হচ্ছে… (প্রথমবার ইঞ্জিন প্রস্তুত হতে কয়েক সেকেন্ড লাগে)")
        Thread {
            var vi: VideoInfo? = null
            var err: String? = null
            for (attempt in 0..1) {
                try {
                    Engine.init(this)
                    val r = YoutubeDLRequest(url).addOption("--no-playlist")
                    val ex = extraBox.text.toString()
                    if (ex.isNotBlank()) r.addCommands(Engine.splitArgs(ex))
                    vi = YoutubeDL.getInstance().getInfo(r); err = null; break
                } catch (e: Exception) {
                    err = e.message?.lines()?.lastOrNull { it.isNotBlank() } ?: "ত্রুটি"
                    if (attempt == 0) { busy("ইঞ্জিন আপডেট করে আবার চেষ্টা হচ্ছে…"); try { Engine.update(this) } catch (_: Exception) {} }
                }
            }
            if (vi == null) { busy(null); runOnUiThread { status.text = "ব্যর্থ: $err" }; return@Thread }
            val v = vi
            runOnUiThread { busy(null); show(v) }
        }.start()
    }

    private fun show(v: VideoInfo) {
        title = v.title ?: "ভিডিও"
        val d = v.duration
        info.text = "$title\n${v.uploader ?: ""}" + if (d > 0) "\nসময়: ${d / 60}:${"%02d".format(d % 60)}" else ""

        val fm = v.formats ?: arrayListOf()
        fun sz(f: com.yausername.youtubedl_android.mapper.VideoFormat) = if (f.fileSize > 0) f.fileSize else f.fileSizeApproximate
        val vids = fm.filter { (it.vcodec ?: "none") != "none" && it.height > 0 }
        val bestAudio = fm.filter { (it.acodec ?: "none") != "none" && (it.vcodec ?: "none") == "none" }.maxOfOrNull { sz(it) } ?: 0L
        val heights = vids.map { it.height }.distinct().sortedDescending()

        val choices = mutableListOf<Choice>()
        for (h in heights) {
            val s = vids.filter { it.height == h }.maxOf { sz(it) }
            val hasAudioInside = vids.filter { it.height == h }.any { (it.acodec ?: "none") != "none" }
            val total = s + if (hasAudioInside) 0 else bestAudio
            val tag = when { h >= 2160 -> " (4K)"; h >= 1440 -> " (2K)"; h >= 1080 -> " (Full HD)"; h >= 720 -> " (HD)"; else -> "" }
            val size = if (total > 0) " • ~%.1f MB".format(total / 1048576.0) else ""
            choices += Choice("${h}p$tag$size", "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]", null)
        }
        if (choices.isEmpty()) choices += Choice("সেরা মান (ডিফল্ট)", "bv*+ba/b", null)

        fun header(t: String) = group.addView(TextView(this).apply { text = "\n$t"; textSize = 14f })
        fun add(c: Choice) = group.addView(RadioButton(this).apply { text = c.label; tag = c; id = View.generateViewId() })
        header("ভিডিও (রেজুলেশন)"); choices.forEach { add(it) }
        header("শুধু অডিও"); add(Choice("MP3 (সেরা মান)", null, "mp3")); add(Choice("M4A (AAC)", null, "m4a"))
        (group.getChildAt(1) as RadioButton).isChecked = true
        startBtn.visibility = View.VISIBLE
    }

    private fun start() {
        val rb = group.findViewById<RadioButton>(group.checkedRadioButtonId) ?: return toast("একটি অপশন বাছুন")
        val c = rb.tag as Choice
        prefs.edit().putString("extra", extraBox.text.toString()).apply()
        val i = Intent(this, DlService::class.java)
            .putExtra("url", Util.firstUrl(urlBox.text.toString())).putExtra("title", title)
            .putExtra("fmt", c.fmt).putExtra("audio", c.audio).putExtra("extra", extraBox.text.toString())
        startForegroundService(i)
        toast("ডাউনলোড শুরু হয়েছে — নোটিফিকেশনে প্রগ্রেস দেখুন")
    }

    private fun updateEngine() {
        busy("ইঞ্জিন আপডেট হচ্ছে…")
        Thread {
            val m = try { Engine.init(this); Engine.update(this) + " (${Engine.version(this)})" } catch (e: Exception) { "আপডেট ব্যর্থ: ${e.message}" }
            busy(null); runOnUiThread { status.text = m }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
