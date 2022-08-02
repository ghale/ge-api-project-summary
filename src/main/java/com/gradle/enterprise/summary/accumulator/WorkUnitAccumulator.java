package com.gradle.enterprise.summary.accumulator;

public interface WorkUnitAccumulator<E, T> extends Accumulator<T>, WithAvoidance {

    void addExecution(E item, String buildId);

    int getTotalExecutions();

}
