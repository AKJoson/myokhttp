package com.cherry.studyokhttp;

import android.util.Log;

import java.util.List;

public class ConnectInterceptor implements Interceptor {
    @Override
    public List<String> intercept(Chain chain) {
        List<String> stringList = chain.processData();
        stringList.add("ConnectInterceptor");
        Log.e("TAG","ConnectInterceptor--1");
        chain.process(stringList);
        Log.e("TAG","ConnectInterceptor--2");
        return stringList;
    }
}
