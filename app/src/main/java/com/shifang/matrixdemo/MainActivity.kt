package com.shifang.matrixdemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.text.MeasuredText
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.util.Printer
import android.util.SparseIntArray
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import androidx.core.util.keyIterator
import com.tencent.matrix.Matrix
import com.tencent.matrix.plugin.Plugin
import com.tencent.matrix.report.Issue
import com.tencent.matrix.trace.TracePlugin
import com.tencent.matrix.trace.config.SharePluginInfo
import com.tencent.matrix.trace.constants.Constants
import com.tencent.matrix.trace.core.AppMethodBeat
import com.tencent.matrix.trace.core.AppMethodBeat.IndexRecord
import com.tencent.matrix.trace.core.UIThreadMonitor
import com.tencent.matrix.trace.items.MethodItem
import com.tencent.matrix.trace.tracer.AnrTracer
import com.tencent.matrix.trace.util.TraceDataUtils
import com.tencent.matrix.trace.util.TraceDataUtils.IStructuredDataFilter
import com.tencent.matrix.trace.util.Utils
import com.tencent.matrix.trace.view.FrameDecorator
import com.tencent.matrix.util.DeviceUtil
import com.tencent.matrix.util.MatrixHandlerThread
import com.tencent.matrix.util.MatrixLog
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val decorator by lazy { FrameDecorator.getInstance(this) }
    data class Test(var a:Int,val b:Int)
    val array = SparseIntArray()
    val array1 = ArrayList<Test>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (canOverlays()) {
            decorator.show()
        } else {
            requestWindowPermission()
        }
//        Looper.getMainLooper().setMessageLogging(HandlerLogger())
//        hookHandler()
//        handler.sendMessage(Message.obtain().apply {
//            this.what = 100
//            this.obj = "i am message"
//        })
        val pluginByClass = Matrix.with().getPluginByClass(TracePlugin::class.java)
        if (!pluginByClass.isPluginStarted) pluginByClass.start()
    }

    private fun hookHandler() {
        val forName = Class.forName("android.app.ActivityThread")
        val field = forName.getDeclaredField("sCurrentActivityThread")
        field.isAccessible = true
        val activityThreadValue = field[forName]
        val mH = forName.getDeclaredField("mH")
        mH.isAccessible = true
        val handler = mH[activityThreadValue]
        val handlerClass: Class<*>? = handler.javaClass.superclass
        if (null != handlerClass) {
            val callbackField = handlerClass.getDeclaredField("mCallback")
            callbackField.isAccessible = true
            val originalCallback = callbackField[handler] as Handler.Callback
            val callback = HookCallback()
            callbackField[handler] = callback
        }
    }

    class HookCallback : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            when (msg.what) {
                100 -> Log.d("handleMessage hook", msg.obj as String)
            }
            return false
        }

    }

    private fun requestWindowPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, 101)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK && canOverlays()) {
            decorator.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val plugin: Plugin = Matrix.with().getPluginByClass(TracePlugin::class.java)
        if (plugin.isPluginStarted) {
            plugin.stop()
        }
        if (decorator.isShowing) {
            decorator.dismiss()
        }
    }

    fun block(view: View) {
        SystemClock.sleep(2_000)
        Toast.makeText(this, "block", Toast.LENGTH_SHORT).show()
        sleeep()
    }

    fun sleeep(){
        Thread.sleep(2_000)
    }

    fun Context.canOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    val anrHandler = Handler(MatrixHandlerThread.getDefaultHandler().getLooper())

    inner class HandlerLogger : Printer {
        var startNs = 0L
        var totalTime = 0L
        val anr = AnrHandleTask()
        override fun println(x: String?) {
            if (x != null) {
                if (x[0] == '>') {
                    startNs = System.nanoTime()
                    anrHandler.postDelayed(anr, 5000)
                } else {
                    totalTime = System.nanoTime() - startNs
                    val second = totalTime / (1000 * 1000 * 1000)
                    if (second > 5) {
                        print("after " + Utils.getStack(Looper.getMainLooper().thread.stackTrace))
                    } else {
                        anrHandler.removeCallbacks(anr)
                    }
                }
            }
        }
    }

    inner class AnrHandleTask : Runnable {
        var beginRecord: IndexRecord? = null
        var token: Long = 0

        constructor() {}
        constructor(record: IndexRecord?, token: Long) {
            beginRecord = record
            this.token = token
        }

        override fun run() {
            println("AnrHandleTask run ")
//            val curTime = SystemClock.uptimeMillis()
////            val isForeground: Boolean = isForeground()
//            // process
//            val processStat = Utils.getProcessPriority(Process.myPid())
////            val data = AppMethodBeat.getInstance().copyData(beginRecord)
////            beginRecord!!.release()
//            val scene = AppMethodBeat.getVisibleScene()

            // memory
//            val memoryInfo: LongArray = dumpMemory()

            // Thread state
            val status = Looper.getMainLooper().thread.state
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val dumpStack = Utils.getStack(stackTrace, "|*\t\t", 12)
            println("zjy dumpStack = ${dumpStack}")
            // frame
//            val monitor = UIThreadMonitor.getMonitor()
//            val inputCost = monitor.getQueueCost(UIThreadMonitor.CALLBACK_INPUT, token)
//            val animationCost = monitor.getQueueCost(UIThreadMonitor.CALLBACK_ANIMATION, token)
//            val traversalCost = monitor.getQueueCost(UIThreadMonitor.CALLBACK_TRAVERSAL, token)
//
//            // trace
//            val stack: LinkedList<MethodItem> = LinkedList<MethodItem>()
////            if (data.size > 0) {
////                TraceDataUtils.structuredDataToStack(data, stack, true, curTime)
////                TraceDataUtils.trimStack(stack, Constants.TARGET_EVIL_METHOD_STACK, object : IStructuredDataFilter {
////                    override fun isFilter(during: Long, filterCount: Int): Boolean {
////                        return during < filterCount * Constants.TIME_UPDATE_CYCLE_MS
////                    }
////
////                    override fun getFilterMaxCount(): Int {
////                        return Constants.FILTER_STACK_MAX_COUNT
////                    }
////
////                    override fun fallback(stack: MutableList<MethodItem>, size: Int) {
////                        val iterator: MutableIterator<*> = stack.listIterator(Math.min(size, Constants.TARGET_EVIL_METHOD_STACK))
////                        while (iterator.hasNext()) {
////                            iterator.next()
////                            iterator.remove()
////                        }
////                    }
////                })
////            }
//            val reportBuilder = StringBuilder()
//            val logcatBuilder = StringBuilder()
//            val stackCost = Math.max(Constants.DEFAULT_ANR.toLong(), TraceDataUtils.stackToString(stack, reportBuilder, logcatBuilder))
//
//            // stackKey
//            val stackKey = TraceDataUtils.getTreeKey(stack, stackCost)
//
//            if (stackCost >= Constants.DEFAULT_ANR_INVALID) {
//                return
//            }
//            // report
//            try {
//                val plugin = Matrix.with().getPluginByClass(TracePlugin::class.java) ?: return
//                var jsonObject = JSONObject()
//                jsonObject = DeviceUtil.getDeviceInfo(jsonObject, Matrix.with().application)
//                jsonObject.put(SharePluginInfo.ISSUE_STACK_TYPE, Constants.Type.ANR)
//                jsonObject.put(SharePluginInfo.ISSUE_COST, stackCost)
//                jsonObject.put(SharePluginInfo.ISSUE_STACK_KEY, stackKey)
//                jsonObject.put(SharePluginInfo.ISSUE_SCENE, scene)
//                jsonObject.put(SharePluginInfo.ISSUE_TRACE_STACK, reportBuilder.toString())
//                jsonObject.put(SharePluginInfo.ISSUE_THREAD_STACK, Utils.getStack(stackTrace))
//                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_PRIORITY, processStat[0])
//                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_NICE, processStat[1])
////                jsonObject.put(SharePluginInfo.ISSUE_PROCESS_FOREGROUND, isForeground)
//                // memory info
//                val memJsonObject = JSONObject()
////                memJsonObject.put(SharePluginInfo.ISSUE_MEMORY_DALVIK, memoryInfo[0])
////                memJsonObject.put(SharePluginInfo.ISSUE_MEMORY_NATIVE, memoryInfo[1])
////                memJsonObject.put(SharePluginInfo.ISSUE_MEMORY_VM_SIZE, memoryInfo[2])
//                jsonObject.put(SharePluginInfo.ISSUE_MEMORY, memJsonObject)
//                val issue = Issue()
//                issue.key = token.toString() + ""
//                issue.tag = SharePluginInfo.TAG_PLUGIN_EVIL_METHOD
//                issue.content = jsonObject
//                plugin.onDetectIssue(issue)
//            } catch (e: JSONException) {
//                MatrixLog.e(AnrTracer.TAG, "[JSONException error: %s", e)
//            }
        }

        private fun printAnr(
            scene: String, processStat: IntArray, memoryInfo: LongArray, state: Thread.State, stack: StringBuilder, isForeground: Boolean, stackSize: Long, stackKey: String, dumpStack: String, inputCost: Long, animationCost: Long, traversalCost: Long, stackCost: Long
        ): String {
            val print = StringBuilder()
            print.append(String.format("-\n>>>>>>>>>>>>>>>>>>>>>>> maybe happens ANR(%s ms)! <<<<<<<<<<<<<<<<<<<<<<<\n", stackCost))
            print.append("|* [Status]").append("\n")
            print.append("|*\t\tScene: ").append(scene).append("\n")
            print.append("|*\t\tForeground: ").append(isForeground).append("\n")
            print.append("|*\t\tPriority: ").append(processStat[0]).append("\tNice: ").append(processStat[1]).append("\n")
            print.append("|*\t\tis64BitRuntime: ").append(DeviceUtil.is64BitRuntime()).append("\n")
            print.append("|* [Memory]").append("\n")
            print.append("|*\t\tDalvikHeap: ").append(memoryInfo[0]).append("kb\n")
            print.append("|*\t\tNativeHeap: ").append(memoryInfo[1]).append("kb\n")
            print.append("|*\t\tVmSize: ").append(memoryInfo[2]).append("kb\n")
            print.append("|* [doFrame]").append("\n")
            print.append("|*\t\tinputCost:animationCost:traversalCost").append("\n")
            print.append("|*\t\t").append(inputCost).append(":").append(animationCost).append(":").append(traversalCost).append("\n")
            print.append("|* [Thread]").append("\n")
            print.append(String.format("|*\t\tStack(%s): ", state)).append(dumpStack)
            print.append("|* [Trace]").append("\n")
            if (stackSize > 0) {
                print.append("|*\t\tStackKey: ").append(stackKey).append("\n")
                print.append(stack.toString())
            } else {
                print.append(String.format("AppMethodBeat is close[%s].", AppMethodBeat.getInstance().isAlive)).append("\n")
            }
            print.append("=========================================================================")
            return print.toString()
        }
    }
}