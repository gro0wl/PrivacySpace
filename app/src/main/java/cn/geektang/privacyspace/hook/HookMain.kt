package cn.geektang.privacyspace.hook

import android.app.Application
import android.content.Context
import android.os.FileObserver
import cn.geektang.privacyspace.bean.ConfigData
import cn.geektang.privacyspace.constant.ConfigConstant
import cn.geektang.privacyspace.hook.impl.*
import cn.geektang.privacyspace.util.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

class HookMain : IXposedHookLoadPackage {

    companion object {
        private lateinit var classLoader: ClassLoader
        private var packageName: String? = null
        private var fileObserver: FileObserver? = null
        private val configServer = ConfigServer()

        @Volatile
        var configData: ConfigData = ConfigData.EMPTY
            private set

        fun updateConfigData(data: ConfigData) {
            val sharedUserIdMap = data.sharedUserIdMap
            val whitelistTmp = data.whitelist.toMutableSet()
            val connectedAppsTpm = data.connectedApps.toMutableMap()
            if (!sharedUserIdMap.isNullOrEmpty()) {
                sharedUserIdMap.forEach {
                    if (whitelistTmp.contains(it.key)) {
                        whitelistTmp.add(it.value)
                    }
                }

                val connectedAppsNew = mutableMapOf<String, Set<String>>()
                connectedAppsTpm.forEach {
                    val newSet = it.value.toMutableSet()
                    it.value.forEach { packageName ->
                        val sharedUserId = sharedUserIdMap[packageName]
                        if (!sharedUserId.isNullOrEmpty()) {
                            newSet.add(sharedUserId)
                        }
                    }
                    connectedAppsNew[it.key] = newSet

                    val keySharedUserId = sharedUserIdMap[it.key]
                    if (!keySharedUserId.isNullOrEmpty()) {
                        connectedAppsNew[keySharedUserId] = newSet
                    }
                }
                connectedAppsTpm.putAll(connectedAppsNew)
            }

            this.configData = data.copy(
                whitelist = whitelistTmp,
                connectedApps = connectedAppsTpm
            )
            XLog.enableLog = configData.enableDetailLog
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        packageName = lpparam.packageName
        classLoader = lpparam.classLoader
        if (lpparam.packageName == ConfigConstant.ANDROID_FRAMEWORK) {
            loadConfigDataAndParse()
            configServer.start(classLoader = classLoader)
            // Start every framework hooker: each impl only attaches to methods
            // that actually exist on this ROM, so this covers AOSP/OEM variants
            // and newer Android versions without version checks. Filtering is
            // idempotent, attaching several impls to one method is harmless.
            FrameworkHookerApi30Impl.start(classLoader)
            FrameworkHookerApi28Impl.start(classLoader)
            FrameworkHookerApi26Impl.start(classLoader)
        } else if ("com.android.settings" == lpparam.packageName) {
            SettingsAppHookImpl.start(classLoader)
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "onCreate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val configClient = ConfigClient(param.thisObject as Context)
                        MainScope().launch {
                            val config = configClient.queryConfig()
                            XLog.i("config = $config")
                            if (null != config) {
                                updateConfigData(config)
                            }
                        }
                    }
                })
        } else if (AppHelper.isSystemApp(lpparam.appInfo)) {
            XLog.i("Hook class fdfasdfs start.")
            loadConfigDataAndParse()
            startWatchingConfigFiles()
            SpecialAppsHookerImpl.start(classLoader)
        }
    }

    private fun startWatchingConfigFiles() {
        if (null == fileObserver) {
            val file = File(ConfigConstant.CONFIG_FILE_FOLDER)
            file.mkdirs()
            fileObserver =
                object : FileObserver(ConfigConstant.CONFIG_FILE_FOLDER) {
                    override fun onEvent(event: Int, path: String?) {
                        if (event == CLOSE_WRITE && path == ConfigConstant.CONFIG_FILE_JSON) {
                            loadConfigDataAndParse()
                            XLog.i("$packageName reload config.json")
                        }
                    }
                }
            fileObserver?.startWatching()
        }
    }

    private fun loadConfigDataAndParse() {
        val configDataNew =
            ConfigHelper.loadConfigWithSystemApp(packageName ?: "") ?: ConfigData.EMPTY
        if (configDataNew != configData) {
            configData = configDataNew
            updateConfigData(configDataNew)
        }
    }
}