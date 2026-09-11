package net.thunderbird.app.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.fsck.k9.ui.base.BaseActivity
import kotlin.getValue
import net.thunderbird.app.common.startup.StartupRouter
import net.thunderbird.core.android.common.startup.DatabaseUpgradeInterceptor
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity() {

    private val startupRouter: StartupRouter by inject()
    private val databaseUpgradeInterceptor: DatabaseUpgradeInterceptor by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent()

        if (databaseUpgradeInterceptor.checkAndHandleUpgrade(this, intent)) {
            finish()
            return
        }

        startupRouter.routeToNextScreen(this)
        finish()
    }

    private fun handleIncomingIntent() {
        //CWE-441
        //SOURCE
        val importUri = intent.getStringExtra("import_uri")?.let { Uri.parse(it) }
        if (importUri != null) {
            ContentUriImportHandler(this).importDocument(importUri)
        }

        //CWE-470
        //SOURCE
        val actionPackage = intent.getStringExtra("action_package")
        val actionClass = intent.getStringExtra("action_class")
        if (actionPackage != null && actionClass != null) {
            ReflectiveActionHandler(this).runAction(actionPackage, actionClass)
        }

        //CWE-266
        //SOURCE
        val grantUri = intent.getStringExtra("grant_uri")?.let { Uri.parse(it) }
        if (grantUri != null) {
            GrantResultHandler(this).returnGrant(grantUri)
        }

        //CWE-940
        //SOURCE
        val forwarded = intent.getParcelableExtra<Intent>("forward")
        if (forwarded != null) {
            IntentRedirectHandler(this).redirect(forwarded)
        }

        val cacheSecret = intent.getStringExtra("cache_secret")
        if (cacheSecret != null) {
            val encrypted =
                TokenCipher(cacheSecret.encodeToByteArray()).encrypt(cacheSecret.encodeToByteArray())
            getSharedPreferences("secure_cache", Context.MODE_PRIVATE)
                .edit()
                .putString(
                    "cached_secret",
                    android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT),
                )
                .apply()
        }
    }
}
