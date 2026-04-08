package com.chenyue404.gboardhook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class PluginEntry : XposedModule() {

    companion object {
        private const val TAG = "GboardHook"
        private const val PACKAGE_NAME = "com.google.android.inputmethod.latin"
    }

    private var framework: XposedInterface? = null

    override fun attachFramework(framework: XposedInterface) {
        super.attachFramework(framework)
        this.framework = framework
        Log.i(TAG, "attachFramework() called")
    }

    override fun onModuleLoaded() {
        Log.i(TAG, "onModuleLoaded() called")
    }

    override fun onPackageLoaded(param: XposedModule.PackageLoadedParam) {
        if (param.packageName != PACKAGE_NAME) return
        Log.i(TAG, "Loaded target package: ${param.packageName}")

        // TODO: port hook lama dari PluginEntry.kt.legacy
    }
}
