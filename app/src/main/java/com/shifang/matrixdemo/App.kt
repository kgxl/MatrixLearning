package com.shifang.matrixdemo

import android.app.Application
import com.tencent.matrix.Matrix
import com.tencent.matrix.trace.TracePlugin
import com.tencent.matrix.trace.config.TraceConfig


/**
 *Created by zjy on 2021/6/23.
 */
class App: Application() {
    override fun onCreate() {
        super.onCreate()
        val dynamicConfig = DynamicConfigImplDemo()
        val fpsEnable: Boolean = dynamicConfig.isFPSEnable()
        val traceEnable: Boolean = dynamicConfig.isTraceEnable()

        val builder = Matrix.Builder(this)
        builder.patchListener(PluginListener(this))

//trace

        val traceConfig = TraceConfig.Builder().dynamicConfig(dynamicConfig) //按照自己需求开启以下监控任务
            .enableFPS(fpsEnable).enableEvilMethodTrace(traceEnable).enableAnrTrace(traceEnable).enableStartup(traceEnable) //一定要写，改成自己项目中的splash页面即可，不然会奔溃
            .splashActivities("com.shifang.matrixdemo.MainActivity;") //debug模式
            .isDebug(true) //dev环境
            .isDevEnv(false).build()

        val tracePlugin = TracePlugin(traceConfig)
        builder.plugin(tracePlugin)



        Matrix.init(builder.build())
        tracePlugin.start()
    }
}