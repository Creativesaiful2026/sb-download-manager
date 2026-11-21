package com.mydl.app

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/** yt-dlp + ffmpeg ইঞ্জিন চালু ও আপডেট। সবসময় ব্যাকগ্রাউন্ড থ্রেডে ডাকতে হবে। */
object Engine {
    @Volatile private var ready = false

    @Synchronized
    fun init(c: Context) {
        if (ready) return
        val app = c.applicationContext
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
        ready = true
        // ইঞ্জিন ৩ দিনের বেশি পুরনো হলে নিজে আপডেট
        val p = app.getSharedPreferences("p", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("eng_upd", 0) > 3L * 24 * 3600 * 1000) {
            try { update(app) } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun update(c: Context): String {
        val app = c.applicationContext
        val r = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
        app.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putLong("eng_upd", System.currentTimeMillis()).apply()
        return if (r == YoutubeDL.UpdateStatus.DONE) "ইঞ্জিন আপডেট হয়েছে" else "ইঞ্জিন আগে থেকেই সর্বশেষ"
    }

    fun version(c: Context): String = try { YoutubeDL.getInstance().versionName(c.applicationContext) ?: "?" } catch (e: Exception) { "?" }

    /** "--a b --c" টেক্সটকে আর্গুমেন্ট তালিকায় ভাঙে (ডবল কোট সমর্থিত) */
    fun splitArgs(s: String): List<String> =
        Regex("\"([^\"]*)\"|(\\S+)").findAll(s).map { it.groups[1]?.value ?: it.groups[2]!!.value }.toList()
}
