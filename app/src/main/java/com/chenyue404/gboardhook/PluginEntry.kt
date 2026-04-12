package com.chenyue404.gboardhook

import android.content.ContentResolver
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
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
            log(Log.ERROR, TAG, "Failed to read module preferences", t)
            null
        }
    }

    // Debug dulu: paksa 50 item
    private fun getClipboardSize(): Int {
        return 50
    }

    // Debug dulu: paksa 7 hari
    private fun getClipboardTime(): Long {
        return 604800000L
    }

    // Debug dulu: log aktif
    private fun isLogEnabled(): Boolean {
        return true
    }

    private fun logInfo(msg: String) {
        if (isLogEnabled()) {
            log(Log.INFO, TAG, msg)
        }
    }

    private fun logError(msg: String, tr: Throwable? = null) {
        log(Log.ERROR, TAG, msg, tr)
    }

    private fun patchSelectionArgs(
        selection: String?,
        selectionArgs: Array<*>?,
        clipboardTime: Long
    ): Array<String>? {
        if (selection == null || selectionArgs == null) return null

        val indexOf = selection.indexOf("timestamp >= ?")
        if (indexOf == -1) return null

        var questionIndexBeforeTarget = 0
        selection.forEachIndexed { i, c ->
            if (i >= indexOf) return@forEachIndexed
            if (c == '?') questionIndexBeforeTarget++
        }

        if (questionIndexBeforeTarget !in selectionArgs.indices) return null

        val copied = selectionArgs.map { it?.toString() ?: "" }.toTypedArray()
        val afterTimeStamp = System.currentTimeMillis() - clipboardTime
        copied[questionIndexBeforeTarget] = afterTimeStamp.toString()

        logInfo(
            "Modified clipboard retention: ${
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
                    .format(Date(afterTimeStamp))
            }"
        )

        return copied
    }

    private fun patchSortOrder(
        sortOrder: String?,
        clipboardSize: Int
    ): String? {
        if (sortOrder == null) return null

        var result = sortOrder

        result = result.replace(
            Regex("(?i)limit\\s+5\\b"),
            "limit $clipboardSize"
        )

        val hasTimestampDesc = Regex("(?i)timestamp\\s+desc").containsMatchIn(result)
        val hasAnyLimit = Regex("(?i)limit\\s+\\d+").containsMatchIn(result)

        if (hasTimestampDesc && !hasAnyLimit) {
            result = "$result limit $clipboardSize"
        }

        if (result != sortOrder) {
            logInfo("Modified sortOrder: $sortOrder -> $result")
        }

        return result
    }

    private fun patchBundleQueryArgs(
        bundle: Bundle?,
        clipboardSize: Int,
        clipboardTime: Long
    ): Bundle? {
        if (bundle == null) return null

        val newBundle = Bundle(bundle)

        val selection = newBundle.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
        val selectionArgs = newBundle.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)
        val sortOrder = newBundle.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)

        val patchedSelectionArgs = patchSelectionArgs(selection, selectionArgs, clipboardTime)
        if (patchedSelectionArgs != null) {
            newBundle.putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                patchedSelectionArgs
            )
        }

        val patchedSortOrder = patchSortOrder(sortOrder, clipboardSize)
        if (patchedSortOrder != null) {
            newBundle.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, patchedSortOrder)
        }

        return newBundle
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        logInfo("onModuleLoaded() process=${param.processName}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != PACKAGE_NAME) return

        logInfo("Loaded target package: ${param.packageName}")

        val classLoader = param.defaultClassLoader

        try {
            val providerClass = classLoader.loadClass(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider"
            )

            val queryMethods = providerClass.declaredMethods.filter { it.name == "query" }

            for (method in queryMethods) {
                hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val args = chain.args.toTypedArray()
                        val clipboardSize = getClipboardSize()
                        val clipboardTime = getClipboardTime()

                        // query(Uri, projection, selection, selectionArgs, sortOrder)
                        if (args.size >= 5) {
                            val selection = args.getOrNull(2) as? String
                            val selectionArgs = args.getOrNull(3) as? Array<*>
                            val sortOrder = args.getOrNull(4) as? String

                            val patchedSelectionArgs =
                                patchSelectionArgs(selection, selectionArgs, clipboardTime)
                            if (patchedSelectionArgs != null) {
                                args[3] = patchedSelectionArgs
                            }

                            val patchedSortOrder = patchSortOrder(sortOrder, clipboardSize)
                            if (patchedSortOrder != null) {
                                args[4] = patchedSortOrder
                            }
                        }

                        // query(Uri, projection, Bundle, CancellationSignal)
                        if (args.size >= 3 && args[2] is Bundle) {
                            val oldBundle = args[2] as Bundle
                            args[2] = patchBundleQueryArgs(oldBundle, clipboardSize, clipboardTime)
                        }

                        chain.proceed(args)
                    }
            }

            logInfo("Installed query hooks: ${queryMethods.size}")
        } catch (t: Throwable) {
            logError("Failed to install query hook", t)
        }
    }
}
