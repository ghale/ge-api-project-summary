package com.gradle.enterprise.summary.accumulator.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.Build;
import com.gradle.enterprise.api.model.MavenAttributes;
import com.gradle.enterprise.api.model.MavenBuildCachePerformance;
import com.gradle.enterprise.api.model.MavenBuildCachePerformanceGoalExecutionEntry;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.accumulator.BuildAccumulator;
import com.gradle.enterprise.summary.formatting.Formatter;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MavenProjectAccumulator implements BuildAccumulator<MavenAttributes, MavenBuildCachePerformance, MavenProjectAccumulator> {

    private final String projectName;
    private final Set<String> userNames = new HashSet<>();
    private final Map<String, GoalTypeAccumulator> goalTypeAccumulatorMap = new LinkedHashMap<>();
    private final Function<String, GoalTypeAccumulator> createGoalTypeAccumulator;

    private int totalNumberOfBuilds = 0;
    private long totalBuildTime = 0;
    private long totalGoalExecutionTime = 0;
    private long totalGoalAvoidanceTime = 0;
    private long totalGoalAvoidableTime = 0;
    private long totalGoalNonAvoidableTime = 0;

    public MavenProjectAccumulator(String projectName) {
        this.projectName = projectName;
        this.createGoalTypeAccumulator = GoalTypeAccumulator::new;
    }

    public Map<String, GoalTypeAccumulator> getGoalTypeAccumulatorMap() {
        return goalTypeAccumulatorMap;
    }

    public long getTotalAvoidableTime() {
        return totalGoalAvoidableTime;
    }

    @Override
    public void addBuild(Build build, MavenAttributes attributes, MavenBuildCachePerformance model) {
        totalNumberOfBuilds++;
        userNames.add(attributes.getEnvironment().getUsername());
        totalBuildTime += model.getBuildTime();

        totalGoalExecutionTime += model.getEffectiveProjectExecutionTime();
        totalGoalAvoidanceTime += model.getAvoidanceSavingsSummary().getTotal();
        totalGoalAvoidableTime += getGoalAvoidableTime(model.getGoalExecution());
        totalGoalNonAvoidableTime += getGoalNonAvoidableTime(model.getGoalExecution());
        addGoalTypes(model.getGoalExecution(), build.getId());
    }

    private void addGoalTypes(List<MavenBuildCachePerformanceGoalExecutionEntry> goalExecutions, String buildId) {
        goalExecutions.forEach(item -> goalTypeAccumulatorMap.computeIfAbsent(item.getMojoType(), createGoalTypeAccumulator).addExecution(item, buildId));
    }

    long getGoalAvoidableTime(List<MavenBuildCachePerformanceGoalExecutionEntry> goalExecution) {
        return goalExecution.stream()
            .filter(item -> item.getAvoidanceOutcome() == MavenBuildCachePerformanceGoalExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_CACHEABLE)
            .collect(Collectors.summarizingLong(MavenBuildCachePerformanceGoalExecutionEntry::getDuration))
            .getSum();
    }

    long getGoalNonAvoidableTime(List<MavenBuildCachePerformanceGoalExecutionEntry> goalExecution) {
        return goalExecution.stream()
            .filter(item ->
                item.getAvoidanceOutcome() == MavenBuildCachePerformanceGoalExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_NOT_CACHEABLE
                    || item.getAvoidanceOutcome() == MavenBuildCachePerformanceGoalExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_UNKNOWN_CACHEABILITY
            )
            .collect(Collectors.summarizingLong(MavenBuildCachePerformanceGoalExecutionEntry::getDuration))
            .getSum();
    }

    @Override
    public Formatter<MavenProjectAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter(mapper, parent);
    }

    public static class JsonFormatter implements Formatter<MavenProjectAccumulator> {
        private final ObjectMapper mapper;
        private final ObjectNode parent;
        private final ObjectNode project;
        private final ObjectNode goals;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            this.mapper = mapper;
            this.parent = parent;
            this.project = mapper.createObjectNode();
            this.goals = mapper.createObjectNode();
        }

        @Override
        public void format(MavenProjectAccumulator model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            project.put("totalNumberOfBuilds", model.totalNumberOfBuilds);
            project.put("buildTime", formatMillis(model.totalBuildTime, shouldFormatDuration));
            project.put("numOfUsers", model.userNames.size());
            project.put("totalGoalExecutionTime", formatMillis(model.totalGoalExecutionTime, shouldFormatDuration));
            project.put("totalGoalAvoidanceTime", formatMillis(model.totalGoalAvoidanceTime, shouldFormatDuration));
            project.put("totalGoalAvoidableTime", formatMillis(model.totalGoalAvoidableTime, shouldFormatDuration));
            project.put("totalGoalNonAvoidableTime", formatMillis(model.totalGoalNonAvoidableTime, shouldFormatDuration));
            parent.set(model.projectName, project);
            project.set("goals", goals);

            applyTransforms(transforms, GoalTypeAccumulator.class, model.getGoalTypeAccumulatorMap().values().stream())
                .forEach(goal -> goal.getFormatter(mapper, goals).formatSafe(goal, shouldFormatDuration, transforms));
        }
    }

    @Override
    public String toString() {
        return "MavenProjectAccumulator{" +
            "projectName='" + projectName + '\'' +
            ", userNames=" + userNames +
            ", goalTypeAccumulatorMap=" + goalTypeAccumulatorMap +
            ", createGoalTypeAccumulator=" + createGoalTypeAccumulator +
            ", totalNumberOfBuilds=" + totalNumberOfBuilds +
            ", totalBuildTime=" + totalBuildTime +
            ", totalGoalExecutionTime=" + totalGoalExecutionTime +
            ", totalGoalAvoidanceTime=" + totalGoalAvoidanceTime +
            ", totalGoalAvoidableTime=" + totalGoalAvoidableTime +
            ", totalGoalNonAvoidableTime=" + totalGoalNonAvoidableTime +
            '}';
    }
}
