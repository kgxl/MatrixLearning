package com.shifang.matrixdemo

import android.content.Context
import android.util.Log
import com.tencent.matrix.plugin.DefaultPluginListener
import com.tencent.matrix.plugin.Plugin
import com.tencent.matrix.report.Issue

/**
 *Created by zjy on 2021/6/23.
 */
class PluginListener(context: Context) : DefaultPluginListener(context) {
    override fun onReportIssue(issue: Issue?) {
        super.onReportIssue(issue)
        Log.e("zjy", "onReportIssue " + issue.toString())
    }

    override fun onInit(plugin: Plugin?) {
        super.onInit(plugin)
        Log.e("zjt","onInit = "+plugin.toString())
    }
    override fun onStart(plugin: Plugin?) {
        super.onStart(plugin)
        Log.e("zjt","onStart = "+plugin.toString())
    }
}