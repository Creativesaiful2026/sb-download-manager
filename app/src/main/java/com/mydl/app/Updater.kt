package com.mydl.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases থেকে সর্বশেষ APK খুঁজে আপডেট করে। */
object Updater {
    fun check(a: Activity, silent: Boolean) {
        Thread {
            try {
                val c = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                val tag = j.getString("tag_name").removePrefix("v")
                val assets = j.getJSONArray("assets")
                var apk: String? = null
                for (i in 0 until assets.length()) {
                    val o = assets.getJSONObject(i)
                    if (o.getString("name").endsWith(".apk")) apk = o.getString("browser_download_url")
                }
                a.runOnUiThread {
                    if (apk != null && newer(tag, BuildConfig.VERSION_NAME)) {
                        AlertDialog.Builder(a).setTitle("নতুন আপডেট $tag")
                            .setMessage("এখনই ডাউনলোড ও ইনস্টল করবেন?")
                            .setPositiveButton("আপডেট") { _, _ -> install(a, apk) }
                            .setNegativeButton("পরে", null).show()
                    } else if (!silent) Toast.makeText(a, "আপনি সর্বশেষ ভার্সনে আছেন", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!silent) a.runOnUiThread { Toast.makeText(a, "চেক ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun install(a: Activity, url: String) {
        val ctx = a.applicationContext
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "এই অ্যাপকে ইনস্টলের অনুমতি দিয়ে আবার আপডেট চাপুন", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        val f = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        f.delete()
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(url)).setTitle("SB Download Manager আপডেট")
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "update.apk")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        )
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                c.unregisterReceiver(this)
                val uri = FileProvider.getUriForFile(c, c.packageName + ".fp", f)
                c.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        val f2 = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f2, Context.RECEIVER_EXPORTED) else ctx.registerReceiver(r, f2)
        Toast.makeText(ctx, "আপডেট ডাউনলোড হচ্ছে…", Toast.LENGTH_SHORT).show()
    }
}
