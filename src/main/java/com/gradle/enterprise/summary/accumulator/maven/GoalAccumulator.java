package com.gradle.enterprise.summary.accumulator.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.MavenBuildCachePerformanceGoalExecutionEntry;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.formatting.Formatter;

import java.util.List;


public class GoalAccumulator {

    protected final String accumulatorName;

    protected int totalExecutions = 0;
    protected long totalGoalExecutionTime = 0;
    protected long totalGoalAvoidanceTime = 0;
    protected long totalGoalAvoidableTime = 0;
    protected long totalGoalNonAvoidableTime = 0;
    protected long cacheMiss = 0;

    public GoalAccumulator(String accumulatorName) {
        this.accumulatorName = accumulatorName;
    }

    public void addExecution(MavenBuildCachePerformanceGoalExecutionEntry item, String buildId) {
        totalExecutions++;
        totalGoalExecutionTime += item.getDuration();
        totalGoalAvoidanceTime += item.getAvoidanceSavings() != null ? item.getAvoidanceSavings() : 0;
        if (item.getAvoidanceOutcome() == MavenBuildCachePerformanceGoalExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_CACHEABLE) {
            // Note that this is not truly all 'avoidable time', as if it was coming from cache, we would get some overhead to be removed from the avoidable time
            totalGoalAvoidableTime += item.getDuration();
            cacheMiss++;
        }
        if (item.getAvoidanceOutcome() == MavenBuildCachePerformanceGoalExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_NOT_CACHEABLE
                || item.getAvoidanceOutcome() == MavenBuildCachePerformanceGoalExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_UNKNOWN_CACHEABILITY) {
            totalGoalNonAvoidableTime += item.getDuration();
        }
    }

    public int getTotalExecutions() {
        return totalExecutions;
    }

    public long getTotalAvoidableTime() {
        return totalGoalAvoidableTime;
    }

    public long getAverageExecutionTime() {
        return totalGoalExecutionTime / totalExecutions;
    }

    public static class JsonFormatter<T extends GoalAccumulator> implements Formatter<T> {
        protected final ObjectNode parent;
        protected final ObjectNode goalSummary;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            this.parent = parent;
            this.goalSummary = mapper.createObjectNode();
        }

        @Override
        public void format(T model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            goalSummary.put("totalExecutions", model.totalExecutions);
            goalSummary.put("totalGoalExecutionTime", formatMillis(model.totalGoalExecutionTime, shouldFormatDuration));
            goalSummary.put("averageGoalExecutionTime", formatMillis(model.getAverageExecutionTime(), shouldFormatDuration));
            goalSummary.put("totalGoalAvoidanceTime", formatMillis(model.totalGoalAvoidanceTime, shouldFormatDuration));
            goalSummary.put("totalGoalAvoidableTime", formatMillis(model.totalGoalAvoidableTime, shouldFormatDuration));
            goalSummary.put("totalGoalNonAvoidableTime", formatMillis(model.totalGoalNonAvoidableTime, shouldFormatDuration));
            goalSummary.put("cacheMissRate", (model.cacheMiss * 100 / model.totalExecutions));
            parent.set(model.accumulatorName, goalSummary);
        }
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
