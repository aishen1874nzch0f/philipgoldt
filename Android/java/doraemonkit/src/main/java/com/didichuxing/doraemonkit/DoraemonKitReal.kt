package com.didichuxing.doraemonkit

import android.app.Application
import android.os.Build
import android.text.TextUtils
import android.util.Log
import com.blankj.utilcode.util.*
import com.blankj.utilcode.util.NetworkUtils.OnNetworkStatusChangedListener
import com.blankj.utilcode.util.ThreadUtils.SimpleTask
import com.didichuxing.doraemonkit.aop.OkHttpHook
import com.didichuxing.doraemonkit.config.GlobalConfig
import com.didichuxing.doraemonkit.config.GpsMockConfig
import com.didichuxing.doraemonkit.config.PerformanceSpInfoConfig
import com.didichuxing.doraemonkit.constant.DokitConstant
import com.didichuxing.doraemonkit.constant.SharedPrefsKey
import com.didichuxing.doraemonkit.datapick.DataPickManager
import com.didichuxing.doraemonkit.kit.AbstractKit
import com.didichuxing.doraemonkit.kit.alignruler.AlignRulerKit
import com.didichuxing.doraemonkit.kit.blockmonitor.BlockMonitorKit
import com.didichuxing.doraemonkit.kit.colorpick.ColorPickerKit
import com.didichuxing.doraemonkit.kit.core.DokitIntent
import com.didichuxing.doraemonkit.kit.core.DokitViewManager
import com.didichuxing.doraemonkit.kit.core.UniversalActivity
import com.didichuxing.doraemonkit.kit.crash.CrashCaptureKit
import com.didichuxing.doraemonkit.kit.dataclean.DataCleanKit
import com.didichuxing.doraemonkit.kit.dbdebug.DbDebugKit
import com.didichuxing.doraemonkit.kit.fileexplorer.FileExplorerKit
import com.didichuxing.doraemonkit.kit.filemanager.FileTransferKit
import com.didichuxing.doraemonkit.kit.gpsmock.GpsMockKit
import com.didichuxing.doraemonkit.kit.gpsmock.GpsMockManager
import com.didichuxing.doraemonkit.kit.gpsmock.ServiceHookManager
import com.didichuxing.doraemonkit.kit.health.AppHealthInfoUtil
import com.didichuxing.doraemonkit.kit.health.HealthKit
import com.didichuxing.doraemonkit.kit.health.model.AppHealthInfo.DataBean.BigFileBean
import com.didichuxing.doraemonkit.kit.largepicture.LargePictureKit
import com.didichuxing.doraemonkit.kit.layoutborder.LayoutBorderKit
import com.didichuxing.doraemonkit.kit.loginfo.LogInfoKit
import com.didichuxing.doraemonkit.kit.main.MainIconDokitView
import com.didichuxing.doraemonkit.kit.methodtrace.MethodCostKit
import com.didichuxing.doraemonkit.kit.network.MockKit
import com.didichuxing.doraemonkit.kit.network.NetworkKit
import com.didichuxing.doraemonkit.kit.network.NetworkManager
import com.didichuxing.doraemonkit.kit.parameter.cpu.CpuKit
import com.didichuxing.doraemonkit.kit.parameter.frameInfo.FrameInfoKit
import com.didichuxing.doraemonkit.kit.parameter.ram.RamKit
import com.didichuxing.doraemonkit.kit.sysinfo.DevelopmentPageKit
import com.didichuxing.doraemonkit.kit.sysinfo.LocalLangKit
import com.didichuxing.doraemonkit.kit.sysinfo.SysInfoKit
import com.didichuxing.doraemonkit.kit.timecounter.TimeCounterKit
import com.didichuxing.doraemonkit.kit.timecounter.instrumentation.HandlerHooker
import com.didichuxing.doraemonkit.kit.toolpanel.KitBean
import com.didichuxing.doraemonkit.kit.toolpanel.KitGroupBean
import com.didichuxing.doraemonkit.kit.toolpanel.KitWrapItem
import com.didichuxing.doraemonkit.kit.toolpanel.ToolPanelUtil
import com.didichuxing.doraemonkit.kit.uiperformance.UIPerformanceKit
import com.didichuxing.doraemonkit.kit.viewcheck.ViewCheckerKit
import com.didichuxing.doraemonkit.kit.weaknetwork.WeakNetworkKit
import com.didichuxing.doraemonkit.kit.webdoor.WebDoorKit
import com.didichuxing.doraemonkit.kit.webdoor.WebDoorManager
import com.didichuxing.doraemonkit.kit.webdoor.WebDoorManager.WebDoorCallback
import com.didichuxing.doraemonkit.util.DokitUtil
import com.didichuxing.doraemonkit.util.DoraemonStatisticsUtil
import com.didichuxing.doraemonkit.util.LogHelper
import com.didichuxing.doraemonkit.util.SharedPrefsUtil
import java.io.File
import java.util.*

/**
 * Created by jintai on 2019/12/18.
 * DoraemonKit ??????????????????  ???????????????app??????
 */
object DoraemonKitReal {
    private const val TAG = "Doraemon"

    /**
     * ??????????????????????????????
     */
    private var sEnableUpload = true
    private var APPLICATION: Application? = null

    fun setDebug(debug: Boolean) {
        LogHelper.setDebug(debug)
    }

    /**
     * @param app
     * @param mapKits  ?????????kits  ?????????????????????????????? ??????????????????mapKits ???????????????????????????mapKits
     * @param listKits  ?????????kits
     * @param productId Dokit??????????????????productId
     */
    fun install(app: Application, mapKits: LinkedHashMap<String, MutableList<AbstractKit>>, listKits: MutableList<AbstractKit>, productId: String) {
        pluginConfig()
        DokitConstant.PRODUCT_ID = productId
        DokitConstant.APP_HEALTH_RUNNING = GlobalConfig.getAppHealth()

        //??????
        APPLICATION = app
        //??????????????????
        initAndroidUtil(app)
        //???????????????
        if (!ProcessUtils.isMainProcess()) {
            return
        }
        val strDokitMode = SharedPrefsUtil.getString(SharedPrefsKey.FLOAT_START_MODE, "normal")
        DokitConstant.IS_NORMAL_FLOAT_MODE = strDokitMode == "normal"
        //????????????????????????
        installLeakCanary(app)
        checkLargeImgIsOpen()
        registerNetworkStatusChangedListener()
        startAppHealth()
        checkGPSMock()

        //??????????????????api??????????????????hook Instrumentation
        HandlerHooker.doHook(app)
        //hook WIFI GPS Telephony????????????
        ServiceHookManager.getInstance().install(app)

        //OkHttp ????????? ??????
        OkHttpHook.installInterceptor()

        //???????????????activity??????????????????
        app.registerActivityLifecycleCallbacks(DokitActivityLifecycleCallbacks())
        //DokitConstant.KIT_MAPS.clear()
        DokitConstant.GLOBAL_KITS.clear()
        //????????????????????????kit
        when {
            mapKits.isNotEmpty() -> {
                mapKits.forEach { map ->
                    val kitWraps: MutableList<KitWrapItem> = map.value.map {
                        KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(it.name), true, map.key, it)
                    } as MutableList<KitWrapItem>

                    DokitConstant.GLOBAL_KITS[map.key] = kitWraps
                }
            }

            mapKits.isEmpty() && listKits.isNotEmpty() -> {
                val kitWraps: MutableList<KitWrapItem> = listKits.map {
                    KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(it.name), true, DokitUtil.getString(R.string.dk_category_biz), it)
                } as MutableList<KitWrapItem>
                DokitConstant.GLOBAL_KITS[DokitUtil.getString(R.string.dk_category_biz)] = kitWraps
            }

        }
        //??????????????????kit ????????????????????????
        ThreadUtils.executeByIo(object : SimpleTask<Any>() {
            override fun doInBackground(): Any {
                addInnerKit(app)
                return Any()
            }

            override fun onSuccess(result: Any?) {
            }
        })

        //addSystemKitForTest(app)
        //???????????????????????????
        DokitViewManager.getInstance().init(app)
        //??????app????????????????????????
        if (sEnableUpload) {
            try {
                DoraemonStatisticsUtil.uploadUserInfo(app)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //????????????
        DataPickManager.getInstance().postData()
    }


    /**
     * ????????????kit
     */
    private fun addInnerKit(application: Application) {
        var json: String?
        if (FileUtils.isFileExists(DokitConstant.SYSTEM_KITS_BAK_PATH)) {
            json = FileIOUtils.readFile2String(DokitConstant.SYSTEM_KITS_BAK_PATH)
            if (TextUtils.isEmpty(json) || json == "[]") {
                val open = application.assets.open("dokit_system_kits.json")
                json = ConvertUtils.inputStream2String(open, "UTF-8")
            }
        } else {
            val open = application.assets.open("dokit_system_kits.json")
            json = ConvertUtils.inputStream2String(open, "UTF-8")
        }

        ToolPanelUtil.jsonConfig2InnerKits(json)
        //???????????????
        DokitConstant.GLOBAL_KITS[DokitUtil.getString(R.string.dk_category_mode)] = mutableListOf()
        //???????????????
        DokitConstant.GLOBAL_KITS[DokitUtil.getString(R.string.dk_category_exit)] = mutableListOf()
        //?????????
        DokitConstant.GLOBAL_KITS[DokitUtil.getString(R.string.dk_category_version)] = mutableListOf()

        //???????????????
        DokitConstant.GLOBAL_KITS.forEach { map ->
            map.value.forEach { kitWrap ->
                kitWrap.kit?.onAppInit(application)
            }
        }
    }


    /**
     * for test
     */
    private fun addSystemKit4Test(application: Application) {

        //????????????
        val platformKits: MutableList<KitWrapItem> = mutableListOf()
        //????????????mock?????? ??????Dokit???????????????????????? ????????????????????????
        val mockKit = MockKit()
        platformKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(mockKit.name), true, "dk_category_platform", mockKit))
        val healKit = HealthKit()
        platformKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(healKit.name), true, "dk_category_platform", healKit))

        val fileSyncKit = FileTransferKit()
        platformKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(fileSyncKit.name), true, "dk_category_platform", fileSyncKit))

        DokitConstant.GLOBAL_KITS["dk_category_platform"] = platformKits

        //????????????
        val commKits: MutableList<KitWrapItem> = mutableListOf()
        //????????????kit
        val sysInfoKit = SysInfoKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(sysInfoKit.name), true, "dk_category_comms", sysInfoKit))
        val developmentPageKit = DevelopmentPageKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(developmentPageKit.name), true, "dk_category_comms", developmentPageKit))
        val localLangKit = LocalLangKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(localLangKit.name), true, "dk_category_comms", localLangKit))
        val fileExplorerKit = FileExplorerKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(fileExplorerKit.name), true, "dk_category_comms", fileExplorerKit))
        val gpsMockKit = GpsMockKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(gpsMockKit.name), true, "dk_category_comms", gpsMockKit))
        val webDoorKit = WebDoorKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(webDoorKit.name), true, "dk_category_comms", webDoorKit))
        val dataCleanKit = DataCleanKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(dataCleanKit.name), true, "dk_category_comms", dataCleanKit))
        val logInfoKit = LogInfoKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(logInfoKit.name), true, "dk_category_comms", logInfoKit))
        val dbDebugKit = DbDebugKit()
        commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(dbDebugKit.name), true, "dk_category_comms", dbDebugKit))

        DokitConstant.GLOBAL_KITS["dk_category_comms"] = commKits

        //weex??????
        val weexKits: MutableList<KitWrapItem> = mutableListOf()
        //????????????weex??????
        try {
            val weexLogKit = Class.forName("com.didichuxing.doraemonkit.weex.log.WeexLogKit").newInstance() as AbstractKit
            commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(weexLogKit.name), true, "dk_category_weex", weexLogKit))
            val storageKit = Class.forName("com.didichuxing.doraemonkit.weex.storage.WeexStorageKit").newInstance() as AbstractKit
            commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(storageKit.name), true, "dk_category_weex", storageKit))
            val weexInfoKit = Class.forName("com.didichuxing.doraemonkit.weex.info.WeexInfoKit").newInstance() as AbstractKit
            commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(weexInfoKit.name), true, "dk_category_weex", weexInfoKit))
            val devToolKit = Class.forName("com.didichuxing.doraemonkit.weex.devtool.WeexDevToolKit").newInstance() as AbstractKit
            commKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(devToolKit.name), true, "dk_category_weex", devToolKit))
            DokitConstant.GLOBAL_KITS["dk_category_weex"] = weexKits
        } catch (e: Exception) {
        }


        //????????????
        val performanceKits: MutableList<KitWrapItem> = mutableListOf()
        //??????????????????kit
        val frameInfoKit = FrameInfoKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(frameInfoKit.name), true, "dk_category_performance", frameInfoKit))
        val cpuKit = CpuKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(cpuKit.name), true, "dk_category_performance", cpuKit))
        val ramKit = RamKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(ramKit.name), true, "dk_category_performance", ramKit))
        val networkKit = NetworkKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(networkKit.name), true, "dk_category_performance", networkKit))
        val crashCaptureKit = CrashCaptureKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(crashCaptureKit.name), true, "dk_category_performance", crashCaptureKit))
        val blockMonitorKit = BlockMonitorKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(blockMonitorKit.name), true, "dk_category_performance", blockMonitorKit))
        val largePictureKit = LargePictureKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(largePictureKit.name), true, "dk_category_performance", largePictureKit))
        val weakNetworkKit = WeakNetworkKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(weakNetworkKit.name), true, "dk_category_performance", weakNetworkKit))
        val timeCounterKit = TimeCounterKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(timeCounterKit.name), true, "dk_category_performance", timeCounterKit))
        val uiPerformanceKit = UIPerformanceKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(uiPerformanceKit.name), true, "dk_category_performance", uiPerformanceKit))
        val methodCostKit = MethodCostKit()
        performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(methodCostKit.name), true, "dk_category_performance", methodCostKit))

        try {
            //????????????leakcanary
            val leakCanaryKit = Class.forName("com.didichuxing.doraemonkit.kit.leakcanary.LeakCanaryKit").newInstance() as AbstractKit
            performanceKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(leakCanaryKit.name), true, "dk_category_performance", leakCanaryKit))
        } catch (e: Exception) {
        }
        DokitConstant.GLOBAL_KITS["dk_category_performance"] = performanceKits


        //????????????
        val uiKits: MutableList<KitWrapItem> = mutableListOf()
        //????????????ui kit
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val colorPickerKit = ColorPickerKit()
            uiKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(colorPickerKit.name), true, "dk_category_ui", colorPickerKit))
        }
        val alignRulerKit = AlignRulerKit()
        uiKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(alignRulerKit.name), true, "dk_category_ui", alignRulerKit))
        val viewCheckerKit = ViewCheckerKit()
        uiKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(viewCheckerKit.name), true, "dk_category_ui", viewCheckerKit))
        val layoutBorderKit = LayoutBorderKit()
        uiKits.add(KitWrapItem(KitWrapItem.TYPE_KIT, DokitUtil.getString(layoutBorderKit.name), true, "dk_category_ui", layoutBorderKit))
        DokitConstant.GLOBAL_KITS["dk_category_ui"] = uiKits
        //????????????
        convert2json()

    }

    /**
     * for test
     */
    private fun convert2json() {
        val localKits = mutableListOf<KitGroupBean>()
        //???????????????
        DokitConstant.GLOBAL_KITS.forEach { map ->
            val kitGroupBean = KitGroupBean(map.key, mutableListOf())
            localKits.add(kitGroupBean)
            map.value.forEach { kitWrap ->
                kitGroupBean.kits.add(KitBean(kitWrap.kit!!::class.java.canonicalName!!, true, kitWrap.kit.innerKitId()))
            }
        }

        val jsonKits = GsonUtils.toJson(localKits)
        LogHelper.i(TAG, jsonKits)
    }


    /**
     * ???????????????????????????????????????
     */
    private fun pluginConfig() {}
    private fun checkGPSMock() {
        if (GpsMockConfig.isGPSMockOpen()) {
            GpsMockManager.getInstance().startMock()
        }
        val latLng = GpsMockConfig.getMockLocation() ?: return
        GpsMockManager.getInstance().mockLocation(latLng.latitude, latLng.longitude)
    }

    /**
     * ????????????????????????1M
     */
    private const val FILE_LENGTH_THRESHOLD = 1 * 1024 * 1024.toLong()

    //todo ????????????1k ???????????????????????????
    //private static long FILE_LENGTH_THRESHOLD = 1024;
    private fun traverseFile(rootFileDir: File?) {
        if (rootFileDir == null) {
            return
        }
        val files = rootFileDir.listFiles()
        files?.forEach { file ->
            if (file.isDirectory) {
                //???????????????????????????????????????????????????
                //LogHelper.i(TAG, "?????????==>" + file.getAbsolutePath());
                traverseFile(file)
            }
            if (file.isFile) {
                //??????????????????????????? byte
                val fileLength = FileUtils.getLength(file)
                if (fileLength > FILE_LENGTH_THRESHOLD) {
                    val fileBean = BigFileBean()
                    fileBean.fileName = FileUtils.getFileName(file)
                    fileBean.filePath = file.absolutePath
                    fileBean.fileSize = "" + fileLength
                    AppHealthInfoUtil.getInstance().addBigFilrInfo(fileBean)
                }
                //LogHelper.i(TAG, "??????==>" + file.getAbsolutePath() + "   fileName===>" + FileUtils.getFileName(file) + " fileLength===>" + fileLength);
            }
        }

    }

    /**
     * ?????????????????????
     * https://blog.csdn.net/csdn_aiyang/article/details/80665185 ????????????????????????????????????
     */
    private fun startBigFileInspect() {
        ThreadUtils.executeByIo(object : SimpleTask<Any?>() {
            @Throws(Throwable::class)
            override fun doInBackground(): Any? {
                val externalCacheDir = APPLICATION!!.externalCacheDir
                if (externalCacheDir != null) {
                    val externalRootDir = externalCacheDir.parentFile
                    traverseFile(externalRootDir)
                }
                val innerCacheDir = APPLICATION!!.cacheDir
                if (innerCacheDir != null) {
                    val innerRootDir = innerCacheDir.parentFile
                    traverseFile(innerRootDir)
                }
                return null
            }

            override fun onSuccess(result: Any?) {}
        })
    }

    /**
     * ??????????????????
     */
    private fun startAppHealth() {
        if (!DokitConstant.APP_HEALTH_RUNNING) {
            return
        }
        if (TextUtils.isEmpty(DokitConstant.PRODUCT_ID)) {
            ToastUtils.showShort("??????????????????????????????????????????????????????")
            return
        }
        AppHealthInfoUtil.getInstance().start()
        //?????????????????????
        startBigFileInspect()
    }

    fun setWebDoorCallback(callback: WebDoorCallback?) {
        WebDoorManager.getInstance().webDoorCallback = callback
    }

    /**
     * ?????????????????????????????????
     */
    private fun registerNetworkStatusChangedListener() {
        NetworkUtils.registerNetworkStatusChangedListener(object : OnNetworkStatusChangedListener {
            override fun onDisconnected() {
                //ToastUtils.showShort("?????????????????????");
                Log.i("Doraemon", "?????????????????????")

            }

            override fun onConnected(networkType: NetworkUtils.NetworkType) {
                //??????DebugDB
                //ToastUtils.showShort("??????????????????:" + networkType.name());
                Log.i("Doraemon", "??????????????????" + networkType.name)

            }
        })
    }

    /**
     * ???????????????????????????????????????
     */
    private fun checkLargeImgIsOpen() {
        if (PerformanceSpInfoConfig.isLargeImgOpen()) {
            NetworkManager.get().startMonitor()
        }
    }

    /**
     * ??????leackCanary
     *
     * @param app
     */
    private fun installLeakCanary(app: Application) {
        //????????????
        try {
            val leakCanaryManager = Class.forName("com.didichuxing.doraemonkit.LeakCanaryManager")
            val install = leakCanaryManager.getMethod("install", Application::class.java)
            //???????????????install??????
            install.invoke(null, app)
            val initAidlBridge = leakCanaryManager.getMethod("initAidlBridge", Application::class.java)
            //????????????initAidlBridge??????
            initAidlBridge.invoke(null, app)
        } catch (e: Exception) {
        }
    }

    private fun initAndroidUtil(app: Application) {
        Utils.init(app)
        LogUtils.getConfig() // ?????? log ?????????????????????????????????????????????????????????
                .setLogSwitch(true) // ????????????????????????????????????????????????
                .setConsoleSwitch(true) // ?????? log ??????????????????????????????????????????????????????????????????????????? log ???????????? tag??? ??????????????????????????? tag ??????????????????????????????????????? tag
                .setGlobalTag("Doraemon") // ?????? log ??????????????????????????????
                .setLogHeadSwitch(true) // ?????? log ??????????????????????????????????????????
                .setLog2FileSwitch(true) // ?????????????????????????????????????????????/cache/log/?????????
                .setDir("") // ????????????????????????????????????"util"?????????????????????"util-MM-dd.txt"
                .setFilePrefix("djx-table-log") // ?????????????????????????????????????????????
                .setBorderSwitch(true) // ??????????????????????????????????????????????????? AS 3.1 ??? Logcat
                .setSingleTagSwitch(true) // log ??????????????????????????? logcat ???????????????????????? Verbose
                .setConsoleFilter(LogUtils.V) // log ????????????????????? logcat ???????????????????????? Verbose
                .setFileFilter(LogUtils.E) // log ????????????????????? 1
                .setStackDeep(2).stackOffset = 0
    }

    /**
     * ?????????????????????icon
     */
    private fun showMainIcon() {
        if (ActivityUtils.getTopActivity() is UniversalActivity) {
            return
        }
        if (!DokitConstant.AWAYS_SHOW_MAIN_ICON) {
            return
        }
        DokitViewManager.getInstance().attachMainIcon()
        DokitConstant.MAIN_ICON_HAS_SHOW = true
    }

    fun show() {
        DokitConstant.AWAYS_SHOW_MAIN_ICON = true
        if (!isShow) {
            showMainIcon()
        }
    }

    /**
     * ??????????????????????????????
     */
    fun showToolPanel() {
        DokitViewManager.getInstance().attachToolPanel()
    }

    fun hideToolPanel() {
        DokitViewManager.getInstance().detachToolPanel()
    }

    fun hide() {
        DokitConstant.MAIN_ICON_HAS_SHOW = false
        DokitConstant.AWAYS_SHOW_MAIN_ICON = false
        DokitViewManager.getInstance().detachMainIcon()
    }

    /**
     * ??????app?????????????????????????????????????????????DoKit?????????????????????????????????????????????app???????????????????????????????????????
     */
    fun disableUpload() {
        sEnableUpload = false
    }

    val isShow: Boolean
        get() = DokitConstant.MAIN_ICON_HAS_SHOW

    /**
     * ??????????????????????????????
     */
    fun setDatabasePass(map: Map<String, String>) {
        DokitConstant.DATABASE_PASS = map
    }

    /**
     * ????????????????????????????????????
     */
    fun setFileManagerHttpPort(port: Int) {
        DokitConstant.FILE_MANAGER_HTTP_PORT = port
    }
}