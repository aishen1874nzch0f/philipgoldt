package com.didichuxing.doraemondemo;

import android.util.Log;

import com.didichuxing.doraemonkit.aop.method_stack.MethodStackUtil;

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/4/22-11:38
 * 描    述：
 * 修订历史：
 * ================================================
 */
public class AopTest {
    private static final String TAG = "AopTest";

//    public void test() {
//        //MethodStackUtil.getInstance().recodeObjectMethodCostStart(1, "AopTest", "test", "desc", this);
//        Log.i(TAG, "================");
//        //MethodStackUtil.getInstance().recodeObjectMethodCostEnd(2, "AopTest", "test", "desc", this);
//    }


    public void test() {
        //long beginTime = System.currentTimeMillis();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //long costTime = System.currentTimeMillis() - beginTime;
    }


}
