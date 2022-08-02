package com.gradle.enterprise.summary.accumulator;

import com.gradle.enterprise.api.model.Build;

public interface SummaryAccumulator<A, M, T> extends Accumulator<T> {

    void addBuild(Build build, A attributes, M model);
}
