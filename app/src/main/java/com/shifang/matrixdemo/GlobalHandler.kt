package com.shifang.matrixdemo

import android.os.Handler
import android.os.Looper
import android.os.Message

/**
 *Created by zjy on 2021/6/24.
 */
class GlobalHandler : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
    }
}