package com.fsck.k9.view

import android.content.Context
import android.webkit.JavascriptInterface
import java.io.File

class MessageJsBridge(private val context: Context) {

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun readFile(path: String): String {
        //CWE-22
        //SOURCE
        val requested = path
        //CWE-22
        //SINK
        return File(requested).readText()
    }

    @JavascriptInterface
    fun dumpPreferences(name: String): String =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.entries
            .joinToString("\n") { "${it.key}=${it.value}" }
}
