package com.gradle.enterprise.summary.processor;

import com.gradle.enterprise.api.model.Build;

public interface BuildProcessor {

    void process(Build build);

}
