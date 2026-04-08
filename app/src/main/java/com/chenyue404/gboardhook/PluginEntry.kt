package com.chenyue404.gboardhook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

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

    override fun attachFramework(framework: XposedInterface) {
        super.attachFramework(framework)
        Log.i(TAG, "attachFramework() called")
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Log.i(TAG, "onModuleLoaded() process=${param.processName}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != PACKAGE_NAME) return
        Log.i(TAG, "Loaded target package: ${param.packageName}")
    }
}