package com.zy.transform;

import android.app.Activity;

import com.blankj.utilcode.util.LogUtils;

/**
 * @Author zhaoya
 * @Date 2023/11/1 12:15
 * @describe
 */
public class ASMTestUtil {
    public static void onCreateLog(Activity activity) {
        LogUtils.d("onCreate: 执行了asm插装" + activity);
    }

    public static void onDestoryLog(Activity activity) {
        LogUtils.d("onDestory: 执行了asm插装" + activity);
    }

    public static void onStartLog(Activity activity) {
        LogUtils.d("onStart: 执行了asm插装" + activity);
    }

    public static void onStopLog(Activity activity) {
        LogUtils.d("onStop: 执行了asm插装" + activity);
    }
}
