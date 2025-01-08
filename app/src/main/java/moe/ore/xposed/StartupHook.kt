package moe.ore.xposed

import android.app.Application
import android.content.Context
import android.os.Build
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.log
import kotlinx.serialization.ExperimentalSerializationApi
import moe.ore.txhook.app.fragment.MainFragment.Packet.CREATOR.QQMUSIC
import moe.ore.txhook.app.fragment.MainFragment.Packet.CREATOR.QQSAFE
import moe.ore.txhook.app.fragment.MainFragment.Packet.CREATOR.WEGAME
import moe.ore.txhook.helper.fastTry
import moe.ore.xposed.main.MainHook
import moe.ore.xposed.util.afterHook
import moe.ore.xposed.util.hookMethod
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.HashMap



object StartupHook {
    private const val TX_FULL_TAG = "tx_full_tag"

    @JvmStatic
    var sec_static_stage_inited = false
    private var firstStageInit = false

    @OptIn(ExperimentalSerializationApi::class)
    fun doInit(source: Int, classLoader: ClassLoader?) {
        if (firstStageInit) return
        if (source == WEGAME || source == QQMUSIC || source == QQSAFE) {
            when (source) {
                WEGAME -> {
                    val LolAppContext = classLoader!!
                        .loadClass("com.tencent.qt.qtl.LolAppContext")
                    LolAppContext.hookMethod("onCreate")?.after {
                        //val clz = FuzzySearchClass.findClassByField("com.tencent.mlol") {
                        //    Modifier.isStatic(it.modifiers) && it.type == Context::class.java
                        //}
                        firstStageInit = true
                        log("hook注入成功(wegame)！")
                        if (it.thisObject != null) {
                            execStartupInit(
                                source,
                                (it.thisObject as Application).applicationContext
                            )
                        }
                    }
                }
                QQMUSIC -> {
                    val MusicApplication = classLoader!!
                        .loadClass("com.tencent.qqmusic.MusicApplication")
                    MusicApplication.hookMethod("onCreate")?.after {
                        //val clz = FuzzySearchClass.findClassByField("com.tencent.mlol") {
                        //    Modifier.isStatic(it.modifiers) && it.type == Context::class.java
                        //}
                        firstStageInit = true
                        log("hook注入成功(qqmusic)！")
                        if (it.thisObject != null) {
                            execStartupInit(
                                source,
                                (it.thisObject as Application).applicationContext
                            )
                        }
                    }
                }
                QQSAFE -> {
                    val RqdApplication = classLoader!!
                        .loadClass("com.tencent.token.global.RqdApplication")
                    RqdApplication.hookMethod("onCreate")?.after {
                        //val clz = FuzzySearchClass.findClassByField("com.tencent.mlol") {
                        //    Modifier.isStatic(it.modifiers) && it.type == Context::class.java
                        //}
                        firstStageInit = true
                        log("hook注入成功(qqsafe)！")
                        if (it.thisObject != null) {
                            execStartupInit(
                                source,
                                (it.thisObject as Application).applicationContext
                            )
                        }
                    }
                }
            }
        } else {
            fastTry {
                val startup = afterHook(51) { param ->
                    fastTry {
                        val clz = param.thisObject.javaClass.classLoader!!
                            .loadClass("com.tencent.common.app.BaseApplicationImpl")
                        val field = clz.declaredFields.first {
                            it.type == clz
                        }
                        val app: Context? = field.get(null) as? Context
                        log("hook注入成功！")
                        if (app != null) {
                            execStartupInit(source, app)
                        }
                    }.onFailure { log(it) }
                }

                val loadDex = findLoadDexTaskClass(classLoader!!)
                loadDex.declaredMethods
                    .filter { it.returnType.equals(java.lang.Boolean.TYPE) && it.parameterTypes.isEmpty() }
                    .forEach {
                        XposedBridge.hookMethod(it, startup)
                    }
                firstStageInit = true
            }.onFailure { log(it) }

            hookMethod(
                "com.tencent.mobileqq.qfix.QFixApplication",
                classLoader,
                "attachBaseContext",
                Context::class.java
            )?.before {
                deleteDirIfNecessaryNoThrow(it.args[0] as Context)
            }
        }
    }

    // Code from QAuxiliary
    @kotlin.Throws(java.lang.ClassNotFoundException::class)
    private fun findLoadDexTaskClass(cl: java.lang.ClassLoader): java.lang.Class<*> {
        try {
            return cl.loadClass("com.tencent.mobileqq.startup.step.LoadDex")
        } catch (ignored: java.lang.ClassNotFoundException) {
            // ignore
        }
        // for NT QQ
        // TODO: 2023-04-19 'com.tencent.mobileqq.startup.task.config.a' is not a good way to find the class
        var kTaskFactory: java.lang.Class<*> = cl.loadClass("com.tencent.mobileqq.startup.task.config.a")
        val kITaskFactory: java.lang.Class<*> = cl.loadClass("com.tencent.qqnt.startup.task.d")
        // check cast so that we can sure that we have found the right class
        if (!kITaskFactory.isAssignableFrom(kTaskFactory)) {
            kTaskFactory = cl.loadClass("com.tencent.mobileqq.startup.task.config.b")
            if (!kITaskFactory.isAssignableFrom(kTaskFactory)) {
                throw java.lang.AssertionError(kITaskFactory.toString() + " is not assignable from " + kTaskFactory)
            }
        }
        var taskClassMapField: Field? = null
        for (field: Field in kTaskFactory.getDeclaredFields()) {
            if (field.getType() === java.util.HashMap::class.java && Modifier.isStatic(field.getModifiers())) {
                taskClassMapField = field
                break
            }
        }
        if (taskClassMapField == null) {
            throw java.lang.AssertionError("taskClassMapField not found")
        }
        taskClassMapField.setAccessible(true)
        val taskClassMap: java.util.HashMap<String, java.lang.Class<*>>
        try {
            // XXX: this will cause <clinit>() to be called, check whether it will cause any problem
            taskClassMap = taskClassMapField.get(null) as java.util.HashMap<String, java.lang.Class<*>>
        } catch (e: java.lang.IllegalAccessException) {
            // should not happen
            throw java.lang.AssertionError(e)
        }
        checkNotNull(taskClassMap)
        val loadDexTaskClass: java.lang.Class<*>? = taskClassMap.get("LoadDexTask")
        if (loadDexTaskClass == null) {
            throw java.lang.AssertionError("loadDexTaskClass not found")
        }
        return loadDexTaskClass
    }

    private fun execStartupInit(source: Int, ctx: Context) {
        if (sec_static_stage_inited) return
        val classLoader: ClassLoader =
            ctx.classLoader ?: throw AssertionError("ERROR: classLoader == null")
        if ("true" == System.getProperty(TX_FULL_TAG)) {
            // reload join
            return
        }
        System.setProperty(TX_FULL_TAG, "true")

        log("进入可执行状态！！！！！")

        // injectClassLoader(classLoader)

        Initiator.init(classLoader)
        MainHook(source, ctx)

        sec_static_stage_inited = true
        deleteDirIfNecessaryNoThrow(ctx)
    }

    private fun injectClassLoader(classLoader: ClassLoader?) {
        requireNotNull(classLoader) { "classLoader == null" }
        try {
            val fParent = ClassLoader::class.java.declaredFields.first { it.name == "parent" }
            fParent.isAccessible = true
            val mine = StartupHook::class.java.classLoader!! // 获取我的loader
            var curr = fParent[mine] as ClassLoader? // 获取我的父loader
            if (curr == null) curr =
                XposedBridge::class.java.classLoader // 如果我的父loader不存在，那么就获取xposed的loader（解决虚拟空间运行问题）
            // if (curr!!.javaClass.name != HybridClassLoader::class.java.name) {
            // fParent[mine] = HybridClassLoader(curr, classLoader)
            // 尝试修复bug
            // }
        } catch (e: Exception) {
            log(e)
        }
    }

    private fun deleteDirIfNecessaryNoThrow(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 24) deleteFile(File(ctx.dataDir, "app_qqprotect"))
        // if (File(ctx.filesDir, "qn_disable_hot_patch").exists()) deleteFile(ctx.getFileStreamPath("hotpatch"))
        // 禁用qq热更新
    }

    private fun deleteFile(file: File): Boolean {
        if (!file.exists()) return false
        if (file.isFile) {
            file.delete()
        } else if (file.isDirectory) {
            file.listFiles()?.forEach { deleteFile(it) }
            file.delete()
        }
        return !file.exists()
    }
}