package com.cherry.studyokhttp;

import android.util.Log;

import java.util.List;

/**
 * the last Interceptor
 */
public class CallServerInterceptor implements Interceptor {

    @Override
    public List<String> intercept(Chain chain) {
        List<String> stringList = chain.processData();
        stringList.add("CallServerInterceptor");
        Log.e("TAG","CallServerInterceptor--1");
//        chain.process(stringList); // if you invoke this method in here, that will throw IndexOutOfBounds Exception.
        return stringList;
    }
}
