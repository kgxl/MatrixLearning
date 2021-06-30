package com.shifang.matrixdemo.Logger

import android.os.Handler
import android.os.Looper
import android.util.Printer
import com.tencent.matrix.trace.util.Utils

/**
 *Created by zjy on 2021/6/24.
 */
class HandlerLogger : Printer {
    var startNs = 0L
    var totalTime = 0L
    val handler = Handler()

    override fun println(x: String?) {
        if (x != null) {
            if (x[0] == '>') {
                startNs = System.nanoTime()
                print("before println " + Looper.getMainLooper().thread.stackTrace)
            } else {
                totalTime = System.nanoTime() - startNs
                val second = totalTime / (1000 * 1000 * 1000)
                if (second > 5) {
                    print("zjy costTime <==> current cost time=>${second}s \n")
                    print("after " + Utils.getStack(Looper.getMainLooper().thread.stackTrace))
                }
            }
        }
    }
}