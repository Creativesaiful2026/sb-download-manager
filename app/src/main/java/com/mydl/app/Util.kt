package com.mydl.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast

object Util {
    const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private val urlRe = Regex("https?://[^\\s]+")
    private val fileRe = Regex("\\.(mp4|mkv|webm|mp3|m4a|aac|ogg|wav|flac|zip|rar|7z|apk|pdf|jpg|jpeg|png|gif|docx?|xlsx?|pptx?|txt)$", RegexOption.IGNORE_CASE)
    private val adHosts = listOf(
        "doubleclick", "googlesyndication", "googleadservices", "adservice.google", "adnxs",
        "taboola", "outbrain", "popads", "propellerads", "exoclick", "adsterra", "moatads",
        "scorecardresearch", "pubmatic", "rubiconproject", "criteo", "amazon-adsystem",
        "adcolony", "unityads", "applovin", "mopub", "popcash", "onclickads", "juicyads"
    )

    fun firstUrl(t: String?): String? = t?.let { urlRe.find(it)?.value }
    fun isFile(u: String): Boolean = fileRe.containsMatchIn(Uri.parse(u).path ?: "")
    private val videoSites = listOf("youtube.com", "youtu.be", "facebook.com", "fb.watch", "instagram.com",
        "tiktok.com", "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "reddit.com", "twitch.tv", "soundcloud.com")
    fun isVideoSite(u: String): Boolean = Uri.parse(u).host?.let { h -> videoSites.any { h == it || h.endsWith(".$it") } } ?: false

    fun openDownloader(c: Context, url: String?) {
        c.startActivity(Intent(c, DownloaderActivity::class.java).putExtra("url", url))
    }

    fun isAd(host: String?): Boolean = host != null && adHosts.any { host.contains(it) }

    fun download(c: Context, url: String, cd: String? = null, mime: String? = null) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val r = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            CookieManager.getInstance().getCookie(url)?.let { r.addRequestHeader("Cookie", it) }
            r.addRequestHeader("User-Agent", UA)
            (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
            Toast.makeText(c, "ডাউনলোড শুরু: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(c, "ডাউনলোড ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun ask(a: Activity, url: String, onOpen: (String) -> Unit, onEnd: () -> Unit = {}) {
        val file = isFile(url)
        val vid = !file && isVideoSite(url)
        val b = AlertDialog.Builder(a).setTitle("লিংক পাওয়া গেছে").setMessage(url)
        when {
            file -> b.setPositiveButton("ডাউনলোড") { _, _ -> download(a, url) }
            vid -> {
                b.setPositiveButton("ভিডিও ডাউনলোড") { _, _ -> openDownloader(a, url) }
                b.setNeutralButton("ব্রাউজারে খুলুন") { _, _ -> onOpen(url) }
            }
            else -> {
                b.setPositiveButton("খুলুন") { _, _ -> onOpen(url) }
                b.setNeutralButton("ভিডিও ডাউনলোড") { _, _ -> openDownloader(a, url) }
            }
        }
        b.setNegativeButton("বাতিল", null).setOnDismissListener { onEnd() }.show()
    }
}
