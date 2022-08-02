package com.gradle.enterprise.summary.accumulator.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.MavenBuildCachePerformanceGoalExecutionEntry;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.accumulator.WorkUnitAccumulator;
import com.gradle.enterprise.summary.formatting.Formatter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


public class GoalNameAccumulator extends GoalAccumulator implements WorkUnitAccumulator<MavenBuildCachePerformanceGoalExecutionEntry, GoalNameAccumulator> {

    public GoalNameAccumulator(String accumulatorName) {
        super(accumulatorName);
    }

    @Override
    public Formatter<GoalNameAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter<>(mapper, parent);
    }

    @Override
    public String toString() {
        return "GoalTypeAccumulator{" +
            "goalName='" + accumulatorName + '\'' +
            ", totalExecutions=" + totalExecutions +
            ", totalGoalExecutionTime=" + totalGoalExecutionTime +
            ", totalGoalAvoidanceTime=" + totalGoalAvoidanceTime +
            ", totalGoalAvoidableTime=" + totalGoalAvoidableTime +
            ", totalGoalNonAvoidableTime=" + totalGoalNonAvoidableTime +
            ", cacheMiss=" + cacheMiss +
            '}';
    }
}
