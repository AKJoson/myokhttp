package com.cherry.studyokhttp;

import java.util.List;

public class RealInterceptorChain implements Interceptor.Chain {

    private int index;
    List<Interceptor> originsInterceptors;
    List<String> mStrings;

    public RealInterceptorChain(List<Interceptor> originsInterceptors, List<String> mStrings , int index) {
        this.originsInterceptors = originsInterceptors;
        this.index = index;
        this.mStrings = mStrings;
    }


    @Override
    public List<String> process(List<String> stringList) {
        RealInterceptorChain realInterceptorChain = new RealInterceptorChain(originsInterceptors, mStrings,index + 1);
        Interceptor interceptor = originsInterceptors.get(index);
        List<String> intercept = interceptor.intercept(realInterceptorChain);
        return intercept;
    }

    @Override
    public List<String> processData() {
        return mStrings;
    }

}
