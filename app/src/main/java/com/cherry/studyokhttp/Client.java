package com.cherry.studyokhttp;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Client {
    public void startControl() {
        List<String> originsDatas = new ArrayList<>();
        originsDatas.add("origing data");
        List<Interceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RetryAndFollowUpInterceptor());
        requestInterceptors.add(new BridgeInterceptor());
        requestInterceptors.add(new CacheInterceptor());
        requestInterceptors.add(new ConnectInterceptor());
        requestInterceptors.add(new CallServerInterceptor());
        RealInterceptorChain realInterceptorChain = new RealInterceptorChain(requestInterceptors,originsDatas,0);
        List<String> response = realInterceptorChain.process(originsDatas);

        for (String s : response) {
            Log.e("TAG","--all over-"+s);
        }
    }
}
