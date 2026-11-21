package com.mydl.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** নোটিফিকেশনে ট্যাপ করলে খোলে; ফোরগ্রাউন্ডে থাকায় ক্লিপবোর্ড পড়তে পারে। */
class ClipActivity : Activity() {
    private var done = false
    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (!f || done) return
        done = true
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val u = Util.firstUrl(cm.primaryClip?.getItemAt(0)?.text?.toString())
        if (u == null) {
            Toast.makeText(this, "ক্লিপবোর্ডে কোনো লিংক নেই", Toast.LENGTH_SHORT).show(); finish(); return
        }
        Util.ask(this, u, {
            startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(it)))
        }, { finish() })
    }
}
