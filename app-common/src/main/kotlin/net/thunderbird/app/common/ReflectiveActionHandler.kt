package net.thunderbird.app.common

import android.content.Context

class ReflectiveActionHandler(private val context: Context) {

    fun runAction(packageName: String, className: String) {
        if (!isAllowedAction(packageName)) return
        val pluginContext =
            context.createPackageContext(
                packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        //CWE-470
        //SINK
        val clazz = pluginContext.classLoader.loadClass(className)
        clazz.getDeclaredConstructor().newInstance()
    }

    private fun isAllowedAction(name: String): Boolean = name.isNotBlank() && !name.contains(' ')
}
