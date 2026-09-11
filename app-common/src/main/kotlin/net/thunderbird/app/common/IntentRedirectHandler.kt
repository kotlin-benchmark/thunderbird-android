package net.thunderbird.app.common

import android.content.Context
import android.content.Intent

class IntentRedirectHandler(private val context: Context) {

    fun redirect(target: Intent) {
        if (!isSafeAction(target)) return
        //CWE-940
        //SINK
        context.startActivity(target)
    }

    private fun isSafeAction(intent: Intent): Boolean {
        val blocked = setOf(Intent.ACTION_CALL, Intent.ACTION_DELETE)
        return intent.action !in blocked
    }
}
