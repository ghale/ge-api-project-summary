package com.gradle.enterprise.summary.accumulator.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.GradleBuildCachePerformanceTaskExecutionEntry;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.formatting.Formatter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gradle.enterprise.api.model.GradleBuildCachePerformanceTaskExecutionEntry.NonCacheabilityCategoryEnum.DISABLED_TO_ENSURE_CORRECTNESS;
import static com.gradle.enterprise.api.model.GradleBuildCachePerformanceTaskExecutionEntry.NonCacheabilityCategoryEnum.OVERLAPPING_OUTPUTS;

public abstract class TaskAccumulator {
    protected final String accumulatorName;

    protected int totalExecutions = 0;
    protected long totalTaskExecutionTime = 0;
    protected long totalTaskAvoidanceTime = 0;
    protected long totalTaskAvoidableTime = 0;
    protected long totalTaskNonAvoidableTime = 0;
    protected long cacheMiss = 0;

    protected final Set<String> nonCacheabilityCategories = new HashSet<>();
    protected final Set<String> nonCacheableBuildIds = new HashSet<>();

    protected TaskAccumulator(String accumulatorName) {
        this.accumulatorName = accumulatorName;
    }

    public void addExecution(GradleBuildCachePerformanceTaskExecutionEntry item, String buildId) {
        totalExecutions++;
        totalTaskExecutionTime += item.getDuration();
        totalTaskAvoidanceTime += item.getAvoidanceSavings() != null ? item.getAvoidanceSavings() : 0;

        if (isAvoidable(item)) {
            // Note that this is not truly all 'avoidable time', as if it was coming from cache, we would get some overhead to be removed from the avoidable time
            totalTaskAvoidableTime += item.getDuration();
            cacheMiss++;
        } else if (item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_NOT_CACHEABLE
            || item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_UNKNOWN_CACHEABILITY) {
            totalTaskNonAvoidableTime += item.getDuration();
        }

        if (isExecutedAvoidable(item)) {
            nonCacheabilityCategories.add(item.getNonCacheabilityCategory().toString());

            if(nonCacheableBuildIds.size() < 50) {
                nonCacheableBuildIds.add(buildId);
            }
        }
    }

    private boolean isAvoidable(GradleBuildCachePerformanceTaskExecutionEntry item) {
        return item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_CACHEABLE
            || isExecutedAvoidable(item);
    }

    protected boolean isExecutedAvoidable(GradleBuildCachePerformanceTaskExecutionEntry item) {
        return item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_NOT_CACHEABLE
            && (item.getNonCacheabilityCategory() == OVERLAPPING_OUTPUTS || item.getNonCacheabilityCategory() == DISABLED_TO_ENSURE_CORRECTNESS);
    }

    public int getTotalExecutions() {
        return totalExecutions;
    }

    public long getTotalAvoidableTime() {
        return totalTaskAvoidableTime;
    }

    public static class JsonFormatter<T extends TaskAccumulator> implements Formatter<T> {
        private final ObjectMapper mapper;
        protected final ObjectNode parent;
        protected final ObjectNode taskSummary;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            this.mapper = mapper;
            this.parent = parent;
            this.taskSummary = mapper.createObjectNode();
        }

        @Override
        public void format(T model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            taskSummary.put("totalExecutions", model.totalExecutions);
            taskSummary.put("totalTaskExecutionTime", formatMillis(model.totalTaskExecutionTime, shouldFormatDuration));
            taskSummary.put("totalTaskAvoidanceTime", formatMillis(model.totalTaskAvoidanceTime, shouldFormatDuration));
            taskSummary.put("totalTaskAvoidableTime", formatMillis(model.totalTaskAvoidableTime, shouldFormatDuration));
            taskSummary.put("totalTaskNonAvoidableTime", formatMillis(model.totalTaskNonAvoidableTime, shouldFormatDuration));
            taskSummary.put("cacheMissRate", (model.cacheMiss * 100 / model.totalExecutions));

            if (!model.nonCacheabilityCategories.isEmpty()) {
                taskSummary.putPOJO("nonCacheabilityCategories", model.nonCacheabilityCategories);
            }

            if(!model.nonCacheableBuildIds.isEmpty()) {
                taskSummary.putPOJO("nonCacheableBuildIds", model.nonCacheableBuildIds);
            }

            parent.set(model.accumulatorName, taskSummary);
        }
    }

    @Override
    public String toString() {
        return "TaskTypeAccumulator{" +
            "name='" + accumulatorName + '\'' +
            ", totalExecutions=" + totalExecutions +
            ", totalTaskExecutionTime=" + totalTaskExecutionTime +
            ", totalTaskAvoidanceTime=" + totalTaskAvoidanceTime +
            ", totalTaskAvoidableTime=" + totalTaskAvoidableTime +
            ", totalTaskNonAvoidableTime=" + totalTaskNonAvoidableTime +
            ", cacheMiss=" + cacheMiss +
            '}';
    }
}
