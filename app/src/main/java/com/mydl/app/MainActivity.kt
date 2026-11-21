package com.mydl.app

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var urlBox: EditText
    private lateinit var bar: ProgressBar
    private lateinit var dl: Button
    private var sniffed: String? = null
    private var lastAsked = ""
    private val prefs by lazy { getSharedPreferences("p", MODE_PRIVATE) }
    private val home = "https://duckduckgo.com"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        askPermissions()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        urlBox = EditText(this).apply {
            setSingleLine(); hint = "লিংক বা সার্চ লিখুন"; imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(text.toString()); true }
        }
        val goBtn = Button(this).apply { text = "→"; setOnClickListener { go(urlBox.text.toString()) } }
        val menuBtn = Button(this).apply { text = "⋮"; setOnClickListener { showMenu(it) } }
        top.addView(urlBox, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(goBtn, LinearLayout.LayoutParams(-2, -2))
        top.addView(menuBtn, LinearLayout.LayoutParams(-2, -2))

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        web = WebView(this)
        dl = Button(this).apply {
            visibility = View.GONE
            setOnClickListener { sniffed?.let { Util.download(this@MainActivity, it) } }
        }
        root.addView(top); root.addView(bar, LinearLayout.LayoutParams(-1, 8))
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(dl)
        setContentView(root)

        setupWeb()
        if (!handle(intent)) web.loadUrl(home)
        if (prefs.getBoolean("mon", true)) startMonitor()
        Updater.check(this, silent = true)
    }

    private fun setupWeb() {
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            userAgentString = Util.UA
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false // পপআপ বন্ধ
        }
        web.setDownloadListener { u, _, cd, m, _ -> Util.download(this, u, cd, m) }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                bar.progress = p; bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (Util.isAd(r.url.host))
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                val u = r.url.toString()
                if (Util.isFile(u)) runOnUiThread {
                    sniffed = u; dl.text = "⬇ ডাউনলোড করুন: " + (Uri.parse(u).lastPathSegment ?: u)
                    dl.visibility = View.VISIBLE
                }
                return null
            }
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean =
                r.url.scheme.let { it != "http" && it != "https" }
            override fun onPageStarted(v: WebView, u: String, f: android.graphics.Bitmap?) {
                urlBox.setText(u); dl.visibility = View.GONE; sniffed = null
            }
        }
    }

    private fun go(s: String) {
        val t = s.trim()
        val u = when {
            t.startsWith("http") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "$home/?q=" + Uri.encode(t)
        }
        web.loadUrl(u)
    }

    private fun handle(i: Intent?): Boolean {
        val u = i?.dataString ?: Util.firstUrl(i?.getStringExtra(Intent.EXTRA_TEXT)) ?: return false
        when {
            Util.isFile(u) -> Util.download(this, u)
            i?.action == Intent.ACTION_SEND && Util.isVideoSite(u) -> Util.openDownloader(this, u)
            else -> go(u)
        }
        return true
    }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); handle(i) }

    // অ্যাপ খোলা অবস্থায় ক্লিপবোর্ডে লিংক থাকলে জিজ্ঞেস করবে
    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (!f || !prefs.getBoolean("mon", true)) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val u = Util.firstUrl(try { cm.primaryClip?.getItemAt(0)?.text?.toString() } catch (e: Exception) { null })
        if (u != null && u != lastAsked) {
            lastAsked = u
            Util.ask(this, u, { go(it) })
        }
    }

    private fun showMenu(anchor: View) {
        val on = prefs.getBoolean("mon", true)
        PopupMenu(this, anchor).apply {
            menu.add(0, 5, 0, "ভিডিও ডাউনলোডার"); menu.add(0, 6, 0, "এই পেজের ভিডিও ডাউনলোড")
            menu.add(0, 1, 0, "হোম"); menu.add(0, 2, 0, "ডাউনলোড তালিকা")
            menu.add(0, 3, 0, if (on) "ক্লিপবোর্ড মনিটর: চালু ✓" else "ক্লিপবোর্ড মনিটর: বন্ধ")
            menu.add(0, 4, 0, "আপডেট চেক করুন")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> web.loadUrl(home)
                    2 -> startActivity(Intent(DownloadManager_VIEW))
                    3 -> {
                        prefs.edit().putBoolean("mon", !on).apply()
                        if (!on) startMonitor() else stopService(Intent(this@MainActivity, MonitorService::class.java))
                    }
                    4 -> Updater.check(this@MainActivity, silent = false)
                    5 -> Util.openDownloader(this@MainActivity, Util.firstUrl(urlBox.text.toString()))
                    6 -> Util.openDownloader(this@MainActivity, web.url)
                }
                true
            }
            show()
        }
    }

    private fun startMonitor() {
        try { startForegroundService(Intent(this, MonitorService::class.java)) } catch (_: Exception) {}
    }

    private fun askPermissions() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 28) need += Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    @Deprecated("") override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }

    companion object { const val DownloadManager_VIEW = android.app.DownloadManager.ACTION_VIEW_DOWNLOADS }
}
