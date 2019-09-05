package com.cherry.studyokhttp;

import android.util.Log;

import java.util.List;

public class CacheInterceptor implements Interceptor {
    @Override
    public List<String> intercept(Chain chain) {
        List<String> stringList = chain.processData();
        stringList.add("CacheInterceptor");
        Log.e("TAG","CacheInterceptor--1");
        chain.process(stringList);
        Log.e("TAG","CacheInterceptor--2");
        return stringList;
    }
}
