package com.didichuxing.doraemonkit.plugin.processor

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import com.didichuxing.doraemonkit.plugin.DoKitExtUtil
import com.didichuxing.doraemonkit.plugin.extension.DoKitExt
import com.didiglobal.booster.gradle.getAndroid
import com.didiglobal.booster.gradle.isDynamicFeature
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.gradle.variantData
import com.didiglobal.booster.task.spi.VariantProcessor
import com.didiglobal.booster.transform.ArtifactManager
import com.didiglobal.booster.transform.artifacts
import com.didiglobal.booster.transform.util.ComponentHandler
import com.google.auto.service.AutoService
import javax.xml.parsers.SAXParserFactory

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/5/15-11:28
 * 描    述：
 * 修订历史：
 * ================================================
 */
@AutoService(VariantProcessor::class)
class DoKitPluginConfigProcessor : VariantProcessor {
    override fun process(variant: BaseVariant) {
        if (variant is LibraryVariant || variant.variantData.isDynamicFeature()) {
            println("error====>dokit plugin config must in the app module")
            return
        }
        //查找AndroidManifest.xml 文件路径
        variant.artifacts.get(ArtifactManager.MERGED_MANIFESTS).forEach { manifest ->
            val parser = SAXParserFactory.newInstance().newSAXParser()
            val handler = ComponentHandler()
            parser.parse(manifest, handler)
            //println("handler====>${handler.applications}")
            DoKitExtUtil.setApplications(handler.applications)
        }


        //读取插件配置
        variant.project.getAndroid<AppExtension>().let { appExt ->
            //查找Application路径
            val doKitExt = variant.project.extensions.getByType(DoKitExt::class.java)
            DoKitExtUtil.init(doKitExt, appExt.defaultConfig.applicationId)
            // println("doKitExt====>$doKitExt  applicationId===>${appExt.defaultConfig.applicationId}")
        }

        //println("variant====>${variant.name}")
    }

}