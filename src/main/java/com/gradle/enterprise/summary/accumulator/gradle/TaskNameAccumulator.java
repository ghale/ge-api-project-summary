package com.gradle.enterprise.summary.accumulator.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.GradleBuildCachePerformanceTaskExecutionEntry;
import com.gradle.enterprise.summary.accumulator.WorkUnitAccumulator;
import com.gradle.enterprise.summary.formatting.Formatter;


public class TaskNameAccumulator extends TaskAccumulator implements WorkUnitAccumulator<GradleBuildCachePerformanceTaskExecutionEntry, TaskNameAccumulator> {

    public TaskNameAccumulator(String accumulatorName) {
        super(accumulatorName);
    }

    @Override
    public Formatter<TaskNameAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter<>(mapper, parent);
    }
}
