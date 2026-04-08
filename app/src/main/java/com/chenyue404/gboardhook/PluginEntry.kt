package com.chenyue404.gboardhook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class PluginEntry : XposedModule() {

    companion object {
        private const val TAG = "GboardHook"
        private const val PACKAGE_NAME = "com.google.android.inputmethod.latin"
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