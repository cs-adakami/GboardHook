package com.chenyue404.gboardhook

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.edit
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.System.loadLibrary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PluginEntry : IXposedHookLoadPackage {
    companion object {
        const val SP_FILE_NAME = "GboardinHook"
        const val SP_KEY = "key"
        const val SP_KEY_LOG = "key_log"
        const val TAG = "xposed-Gboard-hook-"
        const val PACKAGE_NAME = "com.google.android.inputmethod.latin"
        const val DAY: Long = 1000 * 60 * 60 * 24
        const val DEFAULT_NUM = 100
        const val DEFAULT_TIME = DAY * 7
        private const val CLIPBOARD_PROVIDER = "com.google.android.apps.inputmethod.libs.clipboard.ClipboardContentProvider"
        private const val CLIPBOARD_TABLE_KEYWORD = "clipboard_content"
        private val LIMIT_REGEX = Regex("\\blimit\\s+\\d+\\b", RegexOption.IGNORE_CASE)
    }

    init {
        loadLibrary("dexkit")
    }

    private fun getPref(): XSharedPreferences? {
        val pref = XSharedPreferences(BuildConfig.APPLICATION_ID, SP_FILE_NAME)
        return if (pref.file.canRead()) pref else null
    }

    private val clipboardTextSize by lazy {
        getPref()?.getString(SP_KEY, null)?.split(",")?.getOrNull(0)?.toIntOrNull() ?: DEFAULT_NUM
    }

    private val clipboardTextTime by lazy {
        getPref()?.getString(SP_KEY, null)?.split(",")?.getOrNull(1)?.toLongOrNull() ?: DEFAULT_TIME
    }

    private val logSwitch by lazy {
        getPref()?.getBoolean(SP_KEY_LOG, false) ?: false
    }

    private fun log(str: String) {
        if (logSwitch) {
            XposedBridge.log(TAG + "\n" + str)
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val classLoader = lpparam.classLoader

        if (packageName != PACKAGE_NAME &&
            getPref()?.getString(SP_KEY, null)?.split(",")?.getOrNull(2)?.equals("true", true) == false
        ) {
            return
        }

        log("handleLoadPackage: $packageName")

        findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val dexBridge: DexKitBridge by lazy {
                        DexKitBridge.create(classLoader, true)
                    }
                    val context = param.args.first() as Context
                    val sp = context.getSharedPreferences("gboard_hook", Context.MODE_PRIVATE)
                    val spKeyMethod = "SP_KEY_METHOD"
                    val spKeyMethodReadConfig = "SP_KEY_METHOD_READ_CONFIG"
                    val spKeyVersion = "SP_KEY_VERSION"
                    val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                    val gboardVersion = sp.getInt(spKeyVersion, -1)
                    val isSameVersion = versionCode == gboardVersion

                    val methodStr = sp.getString(spKeyMethod, null)
                    val dexMethod: DexMethod? = methodStr?.let {
                        try {
                            DexMethod(it)
                        } catch (e: Exception) {
                            log("dexMethod-$it")
                            XposedBridge.log(e.toString())
                            null
                        }
                    }

                    val adapterMethod = if (isSameVersion && dexMethod != null) {
                        dexMethod
                    } else {
                        val method = findAdapterMethod(dexBridge)
                        if (method != null) {
                            sp.edit {
                                putInt(spKeyVersion, versionCode)
                                putString(spKeyMethod, method.serialize())
                            }
                        }
                        method
                    }
                    adapterMethod?.let { hookAdapter(it, classLoader) }

                    val methodReadConfigStr = sp.getString(spKeyMethodReadConfig, null)
                    val dexMethodReadConfig: DexMethod? = methodReadConfigStr?.let {
                        try {
                            DexMethod(it)
                        } catch (e: Exception) {
                            log("dexMethodReadConfig-$it")
                            XposedBridge.log(e.toString())
                            null
                        }
                    }

                    val readConfigMethod = if (isSameVersion && dexMethodReadConfig != null) {
                        dexMethodReadConfig
                    } else {
                        val method = findReadConfigMethod(dexBridge)
                        if (method != null) {
                            sp.edit {
                                putInt(spKeyVersion, versionCode)
                                putString(spKeyMethodReadConfig, method.serialize())
                            }
                        }
                        method
                    }
                    readConfigMethod?.let { hookReadConfig(it, classLoader) }
                }
            }
        )

        hookClipboardProviderLegacy(classLoader)
        hookClipboardProviderBundle(classLoader)
        hookSQLiteClipboardQueries()
    }

    private fun hookClipboardProviderLegacy(classLoader: ClassLoader) {
        tryHook("$CLIPBOARD_PROVIDER#query-legacy") { name ->
            findAndHookMethod(
                CLIPBOARD_PROVIDER,
                classLoader,
                "query",
                Uri::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log(name)
                        val uri = param.args[0] as Uri
                        val projection = if (param.args[1] != null) param.args[1] as Array<*> else null
                        val selection = param.args[2]?.toString().orEmpty()
                        val selectionArgs = if (param.args[3] != null) param.args[3] as Array<String> else null
                        val sortOrder = param.args[4]?.toString()
                        log("legacy query, uri=$uri, projection=${projection?.joinToString()}, selection=$selection, selectionArgs=${selectionArgs?.joinToString()}, sortOrder=$sortOrder")

                        rewriteSelectionArgs(selection, selectionArgs)?.let {
                            param.args[3] = it
                        }
                        rewriteLimitString(sortOrder)?.let {
                            param.args[4] = it
                            log("legacy limit rewritten: $it")
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        logCursorCount("legacy query", param.result)
                    }
                }
            )
        }
    }

    private fun hookClipboardProviderBundle(classLoader: ClassLoader) {
        tryHook("$CLIPBOARD_PROVIDER#query-bundle") { name ->
            findAndHookMethod(
                CLIPBOARD_PROVIDER,
                classLoader,
                "query",
                Uri::class.java,
                Array<String>::class.java,
                Bundle::class.java,
                CancellationSignal::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log(name)
                        val uri = param.args[0] as Uri
                        val projection = if (param.args[1] != null) param.args[1] as Array<*> else null
                        val queryArgs = (param.args[2] as? Bundle) ?: Bundle()
                        val selection = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION).orEmpty()
                        val selectionArgs = queryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)
                        val sortOrder = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)
                        val sqlLimit = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_LIMIT)
                        log("bundle query, uri=$uri, projection=${projection?.joinToString()}, selection=$selection, selectionArgs=${selectionArgs?.joinToString()}, sortOrder=$sortOrder, sqlLimit=$sqlLimit")

                        rewriteSelectionArgs(selection, selectionArgs)?.let {
                            queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it)
                        }

                        rewriteLimitString(sortOrder)?.let {
                            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, it)
                            log("bundle sort order rewritten: $it")
                        }

                        if (!sqlLimit.isNullOrBlank()) {
                            queryArgs.putString(ContentResolver.QUERY_ARG_SQL_LIMIT, clipboardTextSize.toString())
                            log("bundle sql limit rewritten: ${clipboardTextSize}")
                        }

                        param.args[2] = queryArgs
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        logCursorCount("bundle query", param.result)
                    }
                }
            )
        }
    }

    private fun hookSQLiteClipboardQueries() {
        tryHook("SQLiteDatabase#query-limit") {
            findAndHookMethod(
                SQLiteDatabase::class.java,
                "query",
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        rewriteSqliteQuery(param, 0, 2, 3, 7, "sqlite query")
                    }
                }
            )
        }

        tryHook("SQLiteDatabase#query-limit-cancel") {
            findAndHookMethod(
                SQLiteDatabase::class.java,
                "query",
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                CancellationSignal::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        rewriteSqliteQuery(param, 0, 2, 3, 7, "sqlite query cancel")
                    }
                }
            )
        }

        tryHook("SQLiteDatabase#query-distinct") {
            findAndHookMethod(
                SQLiteDatabase::class.java,
                "query",
                java.lang.Boolean.TYPE,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                Array<String>::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        rewriteSqliteQuery(param, 1, 3, 4, 8, "sqlite query distinct")
                    }
                }
            )
        }
    }

    private fun rewriteSqliteQuery(
        param: XC_MethodHook.MethodHookParam,
        tableIndex: Int,
        selectionIndex: Int,
        selectionArgsIndex: Int,
        limitIndex: Int,
        label: String
    ) {
        val table = param.args.getOrNull(tableIndex)?.toString()
        if (!isClipboardTable(table)) {
            return
        }
        val selection = param.args.getOrNull(selectionIndex)?.toString().orEmpty()
        val selectionArgs = param.args.getOrNull(selectionArgsIndex) as? Array<String>
        val limit = param.args.getOrNull(limitIndex)?.toString()
        log("$label table=$table selection=$selection limit=$limit")

        rewriteSelectionArgs(selection, selectionArgs)?.let {
            param.args[selectionArgsIndex] = it
        }

        param.args[limitIndex] = clipboardTextSize.toString()
        log("$label limit forced to ${clipboardTextSize}")
    }

    private fun isClipboardTable(table: String?): Boolean {
        return table?.lowercase(Locale.ROOT)?.contains(CLIPBOARD_TABLE_KEYWORD) == true
    }

    private fun rewriteSelectionArgs(selection: String, selectionArgs: Array<String>?): Array<String>? {
        if (!selection.contains("timestamp >= ?") || selectionArgs == null) {
            return null
        }
        val indexOf = selection.indexOf("timestamp >= ?")
        var indexOfWhen = 0
        StringBuilder(selection).forEachIndexed { index, c ->
            if (index >= indexOf) return@forEachIndexed
            if (c == '?') {
                indexOfWhen++
            }
        }
        if (indexOfWhen !in selectionArgs.indices) {
            return null
        }
        val updatedArgs = selectionArgs.copyOf()
        val afterTimeStamp = System.currentTimeMillis() - clipboardTextTime
        updatedArgs[indexOfWhen] = afterTimeStamp.toString()
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(afterTimeStamp))
        log("timestamp rewritten: $formatted")
        return updatedArgs
    }

    private fun rewriteLimitString(value: String?): String? {
        if (value.isNullOrBlank() || !LIMIT_REGEX.containsMatchIn(value)) {
            return null
        }
        return LIMIT_REGEX.replace(value, "limit $clipboardTextSize")
    }

    private fun logCursorCount(prefix: String, result: Any?) {
        val cursor = result as? Cursor ?: return
        log("$prefix end, count=${cursor.count}")
    }

    private fun tryHook(logStr: String, unit: (name: String) -> Unit) {
        try {
            unit(logStr)
        } catch (e: NoSuchMethodError) {
            log("NoSuchMethodError--$logStr")
        } catch (e: ClassNotFoundError) {
            log("ClassNotFoundError--$logStr")
        } catch (e: Throwable) {
            log("HookError--$logStr -- $e")
        }
    }

    private fun findAdapterMethod(bridge: DexKitBridge): DexMethod? {
        val methodData = bridge.findClass {
            matcher {
                usingStrings("com/google/android/apps/inputmethod/libs/clipboard/ClipboardAdapter")
                superClass {
                    this.classNameMatcher != null
                }
            }
        }.findMethod {
            matcher {
                usingNumbers(5)
            }
        }.singleOrNull()

        if (methodData == null) {
            log("Can't find adapter")
            return null
        }
        return methodData.toDexMethod()
    }

    private fun hookAdapter(dexMethod: DexMethod, classLoader: ClassLoader) {
        val methodName = dexMethod.name
        val className = dexMethod.className
        val tag = "$className#$methodName"
        log(tag)
        tryHook(tag) {
            findAndHookMethod(
                className,
                classLoader,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log(tag)
                        param.result = null
                    }
                }
            )
        }
    }

    private fun printInfo(tag: String, obj: Any) {
        val p = XposedHelpers.getIntField(obj, "p")
        val x = XposedHelpers.getIntField(obj, "x")
        val y = XposedHelpers.getIntField(obj, "y")
        val list = XposedHelpers.getObjectField(obj, "o") as List<*>
        log("$tag: p=$p, x=$x, y=$y, list.size=${list.size}")
    }

    private fun findReadConfigMethod(bridge: DexKitBridge): DexMethod? {
        val methodData = bridge.findMethod {
            matcher {
                usingStrings("Invalid flag: ")
                returnType("java.lang.Object")
            }
        }.singleOrNull()

        if (methodData == null) {
            log("Can't find ReadConfig")
            return null
        }
        return methodData.toDexMethod()
    }

    private fun hookReadConfig(dexMethod: DexMethod, classLoader: ClassLoader) {
        val methodName = dexMethod.name
        val className = dexMethod.className
        val tag = "$className#$methodName"
        log(tag)
        tryHook(tag) {
            findAndHookMethod(
                className,
                classLoader,
                methodName,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = XposedHelpers.getObjectField(param.thisObject, "a").toString()
                        if (name == "enable_clipboard_entity_extraction" || name == "enable_clipboard_query_refactoring") {
                            param.result = false
                        }
                    }
                }
            )
        }
    }
}
