package com.cherry.studyokhttp;

import android.util.Log;

import java.util.List;

public class BridgeInterceptor implements Interceptor {
    @Override
    public List<String> intercept(Chain chain) {
        List<String> originStrigs = chain.processData();
        originStrigs.add("BridgeInterceptor");
        Log.e("TAG","BridgeInterceptor--1");
        chain.process(originStrigs);
        Log.e("TAG","BridgeInterceptor--2");
        return originStrigs;
    }
}
