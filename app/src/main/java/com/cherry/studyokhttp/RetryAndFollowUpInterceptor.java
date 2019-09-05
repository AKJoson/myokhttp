package com.cherry.studyokhttp;

import android.util.Log;

import java.util.List;

public class RetryAndFollowUpInterceptor implements Interceptor {

    @Override
    public List<String> intercept(Chain chain) {
        List<String> stringList = chain.processData();
        stringList.add("RetryAndFollowUpInterceptor");
        Log.e("TAG","RetryAndFollowUpInterceptor--1");
        chain.process(stringList);
        Log.e("TAG","RetryAndFollowUpInterceptor--2");
        return stringList;
    }
}
