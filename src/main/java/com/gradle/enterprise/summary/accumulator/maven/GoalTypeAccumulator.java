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


public class GoalTypeAccumulator extends GoalAccumulator implements WorkUnitAccumulator<MavenBuildCachePerformanceGoalExecutionEntry, GoalTypeAccumulator> {

    private final Map<String, GoalNameAccumulator> goalNameAccumulatorMap = new LinkedHashMap<>();
    private final Function<String, GoalNameAccumulator> createGoalNameAccumulator = GoalNameAccumulator::new;

    public GoalTypeAccumulator(String accumulatorName) {
        super(accumulatorName);
    }

    @Override
    public void addExecution(MavenBuildCachePerformanceGoalExecutionEntry item, String buildId) {
        super.addExecution(item, buildId);

        var split = item.getGoalName().split(":");
        var name = split[split.length - 1];
        goalNameAccumulatorMap.computeIfAbsent(name, createGoalNameAccumulator).addExecution(item, buildId);
    }

    @Override
    public Formatter<GoalTypeAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter(mapper, parent);
    }

    public static class JsonFormatter extends GoalAccumulator.JsonFormatter<GoalTypeAccumulator> {
        private final ObjectNode goalsByName;
        private final ObjectMapper mapper;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            super(mapper, parent);
            this.goalsByName = mapper.createObjectNode();
            this.mapper = mapper;
        }

        @Override
        public void format(GoalTypeAccumulator model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            super.format(model, shouldFormatDuration, transforms);

            goalSummary.set("goalsByName", goalsByName);

            applyTransforms(transforms, GoalNameAccumulator.class, model.goalNameAccumulatorMap.values().stream())
                .forEach(task -> task.getFormatter(mapper, goalsByName).formatSafe(task, shouldFormatDuration, transforms));
        }
    }

    @Override
    public String toString() {
        return "GoalTypeAccumulator{" +
            "goalType='" + accumulatorName + '\'' +
            ", totalExecutions=" + totalExecutions +
            ", totalGoalExecutionTime=" + totalGoalExecutionTime +
            ", totalGoalAvoidanceTime=" + totalGoalAvoidanceTime +
            ", totalGoalAvoidableTime=" + totalGoalAvoidableTime +
            ", totalGoalNonAvoidableTime=" + totalGoalNonAvoidableTime +
            ", cacheMiss=" + cacheMiss +
            '}';
    }
}
