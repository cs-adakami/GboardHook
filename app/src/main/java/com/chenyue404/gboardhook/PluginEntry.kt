package com.chenyue404.gboardhook

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PluginEntry : XposedModule() {

    companion object {
        const val SP_FILE_NAME = "GboardinHook"
        const val SP_KEY = "key"
        const val SP_KEY_LOG = "key_log"
        const val PACKAGE_NAME = "com.google.android.inputmethod.latin"

        const val DAY: Long = 1000L * 60 * 60 * 24
        const val DEFAULT_NUM = 10
        const val DEFAULT_TIME = DAY * 3

        private const val TAG = "GboardHook"
    }

    private fun getModulePrefs(): SharedPreferences? {
        return try {
            getRemotePreferences(SP_FILE_NAME)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read module preferences", t)
            null
        }
    }

    private fun getClipboardSize(): Int {
        val raw = getModulePrefs()?.getString(SP_KEY, null)
        return raw?.split(",")?.getOrNull(0)?.toIntOrNull() ?: DEFAULT_NUM
    }

    private fun getClipboardTime(): Long {
        val raw = getModulePrefs()?.getString(SP_KEY, null)
        return raw?.split(",")?.getOrNull(1)?.toLongOrNull() ?: DEFAULT_TIME
    }

    private fun isLogEnabled(): Boolean {
        return getModulePrefs()?.getBoolean(SP_KEY_LOG, false) ?: false
    }

    private fun log(msg: String) {
        if (isLogEnabled()) {
            Log.i(TAG, msg)
        }
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Log.i(TAG, "onModuleLoaded() process=${param.processName}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != PACKAGE_NAME) return

        Log.i(TAG, "Loaded target package: ${param.packageName}")

        val classLoader = param.defaultClassLoader

        try {
            val providerClass = classLoader.loadClass(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider"
            )

            val queryMethod: Method = providerClass.getDeclaredMethod(
                "query",
                Uri::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java
            )

            hook(queryMethod)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val args = chain.args.toTypedArray()

                    val selection = args.getOrNull(2) as? String
                    val selectionArgs = args.getOrNull(3) as? Array<String>
                    val sortOrder = args.getOrNull(4) as? String

                    val newArgs = args.copyOf()

                    val clipboardTime = getClipboardTime()
                    val clipboardSize = getClipboardSize()

                    if (selection != null && selectionArgs != null) {
                        val indexOf = selection.indexOf("timestamp >= ?")
                        if (indexOf != -1) {
                            var questionIndexBeforeTarget = 0
                            selection.forEachIndexed { i, c ->
                                if (i >= indexOf) return@forEachIndexed
                                if (c == '?') questionIndexBeforeTarget++
                            }

                            if (questionIndexBeforeTarget in selectionArgs.indices) {
                                val copiedSelectionArgs = selectionArgs.copyOf()
                                val afterTimeStamp = System.currentTimeMillis() - clipboardTime
                                copiedSelectionArgs[questionIndexBeforeTarget] = afterTimeStamp.toString()
                                newArgs[3] = copiedSelectionArgs

                                log(
                                    "Modified clipboard retention: ${
                                        SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss.SSS",
                                            Locale.ROOT
                                        ).format(Date(afterTimeStamp))
                                    }"
                                )
                            }
                        }
                    }

                    if (sortOrder == "timestamp DESC limit 5") {
                        newArgs[4] = "timestamp DESC limit $clipboardSize"
                        log("Modified clipboard capacity: $clipboardSize")
                    }

                    chain.proceed(newArgs)
                }

            Log.i(TAG, "query hook installed")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install query hook", t)
        }
    }
}
