package cn.geektang.privacyspace.util

import android.app.ActivityThread
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Binder
import android.os.ServiceManager
import android.os.SystemProperties
import cn.geektang.privacyspace.BuildConfig
import cn.geektang.privacyspace.bean.SystemUserInfo
import cn.geektang.privacyspace.constant.ConfigConstant
import cn.geektang.privacyspace.hook.HookMain
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.lang.reflect.Method

class ConfigServer : XC_MethodHook() {
    companion object {
        const val QUERY_SERVER_VERSION = "serverVersion"
        const val MIGRATE_OLD_CONFIG_FILE = "migrateOldConfigFile"
        const val QUERY_CONFIG = "queryConfig"
        const val UPDATE_CONFIG = "updateConfig:"
        const val REBOOT_THE_SYSTEM = "rebootTheSystem"
        const val GET_USERS = "getUsers"
        const val FORCE_STOP = "forceStop:"

        const val EXEC_SUCCEED = "1"
        const val EXEC_FAILED = "0"
    }

    private lateinit var classLoader: ClassLoader
    private var pmsClass: Class<*>? = null
    private var userInfoListCache: Collection<*>? = null

    fun start(classLoader: ClassLoader) {
        pmsClass = HookUtil.loadPms(classLoader)
        this.classLoader = classLoader
        if (pmsClass == null) {
            XLog.e("ConfigServer start failed.")
            return
        }

        hookInstallerQueryMethods(pmsClass!!)

        val userManagerClass = try {
            classLoader.tryLoadClass("com.android.server.pm.UserManagerService")
        } catch (e: ClassNotFoundException) {
            XLog.e(e, "Find UserManagerService failed.")
            return
        }
        userManagerClass.declaredMethods.filter { method ->
            method.checkIsGetUsersMethod()
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val userInfoListTmp = param.result as? Collection<*>? ?: return
                    userInfoListCache = userInfoListTmp
                }
            })
        }
    }

    override fun beforeHookedMethod(param: MethodHookParam) {
        when (param.method.name) {
            "getInstallerPackageName" -> {
                hookGetInstallerPackageName(param)
            }
            "getInstallSourceInfo" -> {
                hookGetInstallSourceInfo(param)
            }
            else -> {
            }
        }
    }

    /**
     * IPC channel hooks. Old API (Android 8-11): PMS#getInstallerPackageName(String).
     * On Android 12+ that method is gone and PackageManager#getInstallerPackageName()
     * is routed to getInstallSourceInfo() instead, so both names must be hooked.
     * PMS was heavily refactored on 12+ (methods moved between base/OEM subclasses),
     * so every loadable PMS class and its superclasses are scanned.
     */
    private fun hookInstallerQueryMethods(pms: Class<*>) {
        val hooked = mutableListOf<String>()
        val candidates = mutableSetOf<Class<*>>()
        candidates.add(pms)
        for (name in HookUtil.pmsClassNameArray) {
            classLoader.loadClassSafe(name)?.let { candidates.add(it) }
        }
        for (candidate in candidates) {
            var c: Class<*>? = candidate
            while (c != null && c != Any::class.java) {
                for (method in c.declaredMethods) {
                    if (method.name == "getInstallerPackageName"
                        || method.name == "getInstallSourceInfo"
                    ) {
                        try {
                            XposedBridge.hookMethod(method, this)
                            hooked.add("${c.name}#${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                        } catch (e: Throwable) {
                            XLog.e(e, "Hook ${c.name}#${method.name} failed.")
                        }
                    }
                }
                c = c.superclass
            }
        }
        XLog.i("ConfigServer IPC hooks: ${if (hooked.isEmpty()) "NONE attached!" else hooked.joinToString()}")
    }

    /**
     * New IPC channel (Android 12+): getInstallSourceInfo(String, ...).
     * The client still calls PackageManager#getInstallerPackageName(cmd), which
     * internally delegates to getInstallSourceInfo(cmd).getInstallingPackageName(),
     * so we answer with a synthetic InstallSourceInfo carrying our payload in
     * installingPackageName. The class is taken from the hooked method's return
     * type (no Class.forName: the module classloader can't always see framework
     * classes) and built via the (String, SigningInfo, String, String)
     * constructor present on API 30-34.
     */
    private fun hookGetInstallSourceInfo(param: MethodHookParam) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != getPackageUid(BuildConfig.APPLICATION_ID) && callingUid != getPackageUid("com.android.settings")
        ) {
            return
        }
        val firstArg = param.args.firstOrNull()?.toString() ?: return
        val payload = resolveServerPayload(firstArg) ?: return
        val returnType = (param.method as? java.lang.reflect.Method)?.returnType
            ?: return
        val info = newInstallSourceInfo(payload, returnType) ?: return
        param.result = info
        XLog.i("ConfigServer served '${firstArg.take(24)}' (${payload.length} chars).")
    }

    /** Returns the payload for our internal commands, or null for real packages. */
    private fun resolveServerPayload(firstArg: String): String? {
        return when {
            firstArg == QUERY_SERVER_VERSION -> BuildConfig.VERSION_CODE.toString()
            firstArg == MIGRATE_OLD_CONFIG_FILE -> {
                tryMigrateOldConfig()
                ""
            }
            firstArg == QUERY_CONFIG -> queryConfig()
            firstArg == REBOOT_THE_SYSTEM -> {
                SystemProperties.set("sys.powerctl", "reboot")
                ""
            }
            firstArg == GET_USERS -> {
                val systemUsers = mutableListOf<SystemUserInfo>()
                userInfoListCache?.filterIsInstance<UserInfo>()?.forEach { userInfo ->
                    systemUsers.add(
                        SystemUserInfo(
                            id = userInfo.id,
                            name = userInfo.name
                        )
                    )
                }
                JsonHelper.systemUserInfoListAdapter().toJson(systemUsers)
            }
            firstArg.startsWith(UPDATE_CONFIG) -> {
                val arg = firstArg.substring(UPDATE_CONFIG.length)
                updateConfig(arg)
                ""
            }
            firstArg.startsWith(FORCE_STOP) -> {
                val arg = firstArg.substring(FORCE_STOP.length)
                forceStopPackage(arg)
            }
            else -> null
        }
    }

    private fun newInstallSourceInfo(
        installingPackageName: String,
        installSourceInfoClass: Class<*>
    ): Any? {
        return try {
            var target: java.lang.reflect.Constructor<*>? = null
            for (ctor in installSourceInfoClass.declaredConstructors) {
                val params = ctor.parameterTypes
                if (params.size == 4
                    && params[0] == String::class.java
                    && params[2] == String::class.java
                    && params[3] == String::class.java
                    && params[1].name == "android.content.pm.SigningInfo"
                ) {
                    target = ctor
                    break
                }
            }
            if (target == null) {
                XLog.e("InstallSourceInfo 4-arg constructor not found in ${installSourceInfoClass.name}.")
                return null
            }
            target.isAccessible = true
            target.newInstance(null, null, null, installingPackageName)
        } catch (e: Throwable) {
            XLog.e(e, "Create InstallSourceInfo failed.")
            null
        }
    }

    private fun hookGetInstallerPackageName(param: MethodHookParam) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != getPackageUid(BuildConfig.APPLICATION_ID) && callingUid != getPackageUid("com.android.settings")
        ) {
            return
        }
        val firstArg = param.args.first()?.toString() ?: return
        when {
            firstArg == QUERY_SERVER_VERSION -> {
                param.result = BuildConfig.VERSION_CODE.toString()
            }
            firstArg == MIGRATE_OLD_CONFIG_FILE -> {
                tryMigrateOldConfig()
                param.result = ""
            }
            firstArg == QUERY_CONFIG -> {
                param.result = queryConfig()
            }
            firstArg == REBOOT_THE_SYSTEM -> {
                SystemProperties.set("sys.powerctl", "reboot")
                param.result = ""
            }
            firstArg == GET_USERS -> {
                val users = userInfoListCache
                val systemUsers = mutableListOf<SystemUserInfo>()
                users?.forEach { userInfo ->
                    if (userInfo !is UserInfo) return
                    val systemUserInfo = SystemUserInfo(
                        id = userInfo.id,
                        name = userInfo.name
                    )
                    systemUsers.add(systemUserInfo)
                }
                param.result = JsonHelper.systemUserInfoListAdapter().toJson(systemUsers)
            }
            firstArg.startsWith(UPDATE_CONFIG) -> {
                val arg = firstArg.substring(UPDATE_CONFIG.length)
                updateConfig(arg)
                param.result = ""
            }
            firstArg.startsWith(FORCE_STOP) -> {
                val arg = firstArg.substring(FORCE_STOP.length)
                param.result = forceStopPackage(arg)
            }
        }
    }

    private fun forceStopPackage(packageName: String): String {
        XLog.d("forceStopPackage = $packageName")
        val callingUid = Binder.getCallingUid()
        val ams = ServiceManager.getService("activity")
        val checkPermissionUnhook = XposedHelpers.findAndHookMethod(
            ams.javaClass,
            "checkPermission",
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uid = param.args[2]
                    if (callingUid == uid) {
                        param.result = PackageManager.PERMISSION_GRANTED
                    }
                }
            }
        )

        val isExecSucceed = try {
            val method = ams.javaClass.getDeclaredMethod(
                "forceStopPackage",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(ams, packageName, 0)
            EXEC_SUCCEED
        } catch (e: Throwable) {
            XLog.e(e, "forceStopPackage $packageName failed.")
            EXEC_FAILED
        } finally {
            checkPermissionUnhook.unhook()
        }
        return isExecSucceed
    }

    private fun queryConfig(): String {
        val configFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER}${ConfigConstant.CONFIG_FILE_JSON}")
        return try {
            configFile.readText()
        } catch (e: Exception) {
            ""
        }
    }

    private fun updateConfig(configJson: String) {
        val configFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER}${ConfigConstant.CONFIG_FILE_JSON}")
        configFile.parentFile?.mkdirs()
        try {
            val configData = JsonHelper.configAdapter().fromJson(configJson)
            if (null != configData) {
                HookMain.updateConfigData(configData)
                configFile.writeText(configJson)
            }
        } catch (e: Exception) {
            XLog.e(e, "Update config error.")
        }
    }

    private fun getPackageUid(packageName: String): Int {
        return try {
            ActivityThread.getPackageManager()
                .getPackageUid(packageName, 0, 0)
        } catch (e: Throwable) {
            XLog.d("ConfigServer (${Binder.getCallingUid()}).getClientUid failed.")
            -1
        }
    }

    private fun tryMigrateOldConfig() {
        val originalFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER_ORIGINAL}${ConfigConstant.CONFIG_FILE_JSON}")
        val newConfigFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER}${ConfigConstant.CONFIG_FILE_JSON}")
        if (!newConfigFile.exists()) {
            newConfigFile.parentFile?.mkdirs()
            originalFile.copyTo(newConfigFile)
        }
    }

    private fun Method.checkIsGetUsersMethod(): Boolean {
        if (name != "getUsers") {
            return false
        }
        var isGetUsersMethod = false
        if (parameterCount == 1 && parameterTypes.first() == Boolean::class.javaPrimitiveType) {
            isGetUsersMethod = true
        } else if (parameterCount == 3) {
            isGetUsersMethod = true
            for (parameterType in parameterTypes) {
                if (parameterType != Boolean::class.javaPrimitiveType) {
                    isGetUsersMethod = false
                    break
                }
            }
        }
        return isGetUsersMethod
    }
}