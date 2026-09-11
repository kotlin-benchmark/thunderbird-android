package net.thunderbird.app.common

import android.app.Activity
import android.content.Intent
import android.net.Uri

class GrantResultHandler(private val activity: Activity) {

    fun returnGrant(uri: Uri) {
        val result = Intent().apply {
            data = uri
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        //CWE-266
        //SINK
        activity.setResult(Activity.RESULT_OK, result)
    }
}
