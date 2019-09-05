package com.cherry.studyokhttp;

import java.util.List;

public interface Interceptor {
    List<String> intercept(Chain chain);

    interface Chain{
        List<String> process(List<String> stringList);
        List<String> processData();
    }
}
