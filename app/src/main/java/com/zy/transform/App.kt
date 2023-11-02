package com.zy.transform

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

/**
 * @Author zhaoya
 * @Date 2023/10/26 15:52
 * @describe
 */
class App: Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}