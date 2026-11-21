package com.mydl.app

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** yt-dlp দিয়ে ব্যাকগ্রাউন্ডে ডাউনলোড; প্রগ্রেস নোটিফিকেশনে দেখায়। */
class DlService : Service() {
    private val pool = Executors.newFixedThreadPool(2)
    private val active = AtomicInteger(0)
    private val ids = AtomicInteger(100)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("dl", "ভিডিও ডাউনলোড", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i == null) return START_NOT_STICKY
        if (i.action == "cancel") {
            i.getStringExtra("pid")?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            return START_NOT_STICKY
        }
        val url = i.getStringExtra("url") ?: return START_NOT_STICKY
        val title = i.getStringExtra("title") ?: "ভিডিও"
        val fmt = i.getStringExtra("fmt")        // ভিডিওর ফরম্যাট সিলেক্টর
        val audio = i.getStringExtra("audio")    // mp3 / m4a
        val extra = i.getStringExtra("extra") ?: ""
        val nid = ids.incrementAndGet()
        val pid = "job$nid"

        val base = Notification.Builder(this, "dl").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ডাউনলোড চলছে").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(10, base, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(10, base)

        active.incrementAndGet()
        pool.execute {
            try { run(url, title, fmt, audio, extra, nid, pid) }
            finally { if (active.decrementAndGet() == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }
        return START_NOT_STICKY
    }

    private fun notif(nid: Int, title: String, text: String, pct: Int?, pid: String?, done: Boolean = false) {
        val b = Notification.Builder(this, "dl").setSmallIcon(
            if (done) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download
        ).setContentTitle(title).setContentText(text).setOnlyAlertOnce(true).setOngoing(!done).setAutoCancel(done)
        if (pct != null) b.setProgress(100, pct, pct < 0)
        if (pid != null) {
            val ci = Intent(this, DlService::class.java).setAction("cancel").putExtra("pid", pid)
            b.addAction(Notification.Action.Builder(null, "বাতিল",
                PendingIntent.getService(this, nid, ci, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
        }
        if (done) b.setContentIntent(PendingIntent.getActivity(this, nid,
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), PendingIntent.FLAG_IMMUTABLE))
        getSystemService(NotificationManager::class.java).notify(nid, b.build())
    }

    private fun run(url: String, title: String, fmt: String?, audio: String?, extra: String, nid: Int, pid: String) {
        val dir = File(getExternalFilesDir("tmp") ?: filesDir, pid).apply { mkdirs() }
        try {
            notif(nid, title, "ইঞ্জিন প্রস্তুত হচ্ছে…", -1, pid)
            Engine.init(this)
            val r = YoutubeDLRequest(url)
            r.addOption("--no-playlist"); r.addOption("--no-mtime"); r.addOption("--retries", 5)
            r.addOption("-N", 4)
            r.addOption("-o", dir.absolutePath + "/%(title).80B.%(ext)s")
            if (audio != null) {
                r.addOption("-f", if (audio == "m4a") "bestaudio[ext=m4a]/bestaudio/best" else "bestaudio/best")
                r.addOption("-x"); r.addOption("--audio-format", audio); r.addOption("--audio-quality", 0)
            } else {
                r.addOption("-f", fmt ?: "bv*+ba/b")
                r.addOption("--merge-output-format", "mp4/mkv")
            }
            if (extra.isNotBlank()) r.addCommands(Engine.splitArgs(extra))

            var last = -1
            YoutubeDL.getInstance().execute(r, pid) { p, eta, _ ->
                val pc = p.toInt()
                if (pc != last && pc >= 0) {
                    last = pc
                    notif(nid, title, "$pc%" + if (eta > 0) " • বাকি ${eta}s" else "", pc, pid)
                }
            }
            notif(nid, title, "ফাইল সংরক্ষণ হচ্ছে…", -1, null)
            val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") } ?: emptyList()
            if (files.isEmpty()) throw Exception("কোনো ফাইল পাওয়া যায়নি")
            files.forEach { save(it) }
            notif(nid, title, "ডাউনলোড সম্পন্ন ✓ (Downloads/SB Download Manager)", null, null, true)
        } catch (e: YoutubeDL.CanceledException) {
            notif(nid, title, "বাতিল করা হয়েছে", null, null, true)
        } catch (e: Exception) {
            val m = (e.message ?: "অজানা ত্রুটি").lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "ত্রুটি"
            notif(nid, title, "ব্যর্থ: $m", null, null, true)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** টেম্প ফোল্ডার থেকে পাবলিক Downloads/SB Download Manager এ সরায় */
    private fun save(f: File) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SB Download Manager")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw Exception("সংরক্ষণ ব্যর্থ")
            contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        } else {
            val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SB Download Manager").apply { mkdirs() }
            f.copyTo(File(d, f.name), overwrite = true)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null
}
