package com.dansheng.notifyenh.util

import android.util.Log
import com.dansheng.notifyenh.App
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object LogUtils {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun d(message: String, throwable: Throwable? = null) {
        Log.d("NotifyEnh", message, throwable)
        logToDb(message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e("NotifyEnh", message, throwable)
        logToDb(message, throwable)
    }

    fun logToDb(message: String, throwable: Throwable? = null) {
        scope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(App.instance).logDao()
                .insert(
                    LogEntity(
                        time = System.currentTimeMillis(),
                        message = message,
                        stackTrace = throwable?.stackTraceToString()
                    )
                )
        }
    }

}