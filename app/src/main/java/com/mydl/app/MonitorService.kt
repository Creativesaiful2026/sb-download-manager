package com.mydl.app

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class MonitorService : Service() {
    private lateinit var cm: ClipboardManager
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        // Android 10+ এ ব্যাকগ্রাউন্ডে টেক্সট পড়া যায় না, তাই ট্যাপ করলে ClipActivity পড়বে
        val u = try { Util.firstUrl(cm.primaryClip?.getItemAt(0)?.text?.toString()) } catch (e: Exception) { null }
        notifyClip(u)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mon", "ক্লিপবোর্ড মনিটর", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("clip", "কপি করা লিংক", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "mon")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ক্লিপবোর্ড মনিটর চালু")
            .setContentText("লিংক কপি করলে এখানে জানানো হবে")
            .setContentIntent(open()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(listener)
    }

    private fun open() = PendingIntent.getActivity(
        this, 0, Intent(this, ClipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notifyClip(u: String?) {
        val n = Notification.Builder(this, "clip")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("লিংক কপি হয়েছে")
            .setContentText(u ?: "ডাউনলোড করতে ট্যাপ করুন")
            .setAutoCancel(true).setContentIntent(open()).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }

    override fun onDestroy() { cm.removePrimaryClipChangedListener(listener); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
