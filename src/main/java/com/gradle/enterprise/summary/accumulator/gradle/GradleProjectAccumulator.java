package com.gradle.enterprise.summary.accumulator.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.Build;
import com.gradle.enterprise.api.model.GradleAttributes;
import com.gradle.enterprise.api.model.GradleBuildCachePerformance;
import com.gradle.enterprise.api.model.GradleBuildCachePerformanceTaskExecutionEntry;
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

public class GradleProjectAccumulator implements BuildAccumulator<GradleAttributes, GradleBuildCachePerformance, GradleProjectAccumulator> {

    private final String projectName;

    private int totalNumberOfBuilds = 0;

    private final Set<String> userNames = new HashSet<>();
    private long totalBuildTime = 0;
    private long totalTaskExecutionTime = 0;
    private long totalTaskAvoidanceTime = 0;
    private long totalTaskAvoidableTime = 0;
    private long totalTaskNonAvoidableTime = 0;

    private final Map<String, TaskTypeAccumulator> taskTypeAccumulatorMap = new LinkedHashMap<>();
    private final Function<String, TaskTypeAccumulator> createTaskTypeAccumulator;

    public GradleProjectAccumulator(String projectName) {
        this.projectName = projectName;
        this.createTaskTypeAccumulator = TaskTypeAccumulator::new;
    }

    public Map<String, TaskTypeAccumulator> getTaskTypeAccumulatorMap() {
        return taskTypeAccumulatorMap;
    }

    public long getTotalAvoidableTime() {
        return totalTaskAvoidableTime;
    }

    @Override
    public void addBuild(Build build, GradleAttributes attributes, GradleBuildCachePerformance model) {
        totalNumberOfBuilds++;
        userNames.add(attributes.getEnvironment().getUsername());
        totalBuildTime += model.getBuildTime();
        totalTaskExecutionTime += model.getEffectiveTaskExecutionTime();
        totalTaskAvoidanceTime += model.getAvoidanceSavingsSummary().getTotal();
        totalTaskAvoidableTime += getTaskAvoidableTime(model.getTaskExecution());
        totalTaskNonAvoidableTime += getTaskNonAvoidableTime(model.getTaskExecution());
        addTaskTypes(model.getTaskExecution(), build.getId());
    }

    private void addTaskTypes(List<GradleBuildCachePerformanceTaskExecutionEntry> taskExecution, String buildId) {
        taskExecution.forEach(item -> taskTypeAccumulatorMap.computeIfAbsent(item.getTaskType(), createTaskTypeAccumulator).addExecution(item, buildId));
    }

    long getTaskAvoidableTime(List<GradleBuildCachePerformanceTaskExecutionEntry> taskExecution) {
        return taskExecution.stream()
            .filter(item -> item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_CACHEABLE)
            .collect(Collectors.summarizingLong(GradleBuildCachePerformanceTaskExecutionEntry::getDuration))
            .getSum();
    }

    long getTaskNonAvoidableTime(List<GradleBuildCachePerformanceTaskExecutionEntry> taskExecution) {
        return taskExecution.stream()
            .filter(item ->
                item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_NOT_CACHEABLE
                    || item.getAvoidanceOutcome() == GradleBuildCachePerformanceTaskExecutionEntry.AvoidanceOutcomeEnum.EXECUTED_UNKNOWN_CACHEABILITY
            )
            .collect(Collectors.summarizingLong(GradleBuildCachePerformanceTaskExecutionEntry::getDuration))
            .getSum();
    }

    @Override
    public Formatter<GradleProjectAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter(mapper, parent);
    }

    public static class JsonFormatter implements Formatter<GradleProjectAccumulator> {
        private final ObjectMapper mapper;
        private final ObjectNode parent;
        private final ObjectNode project;
        private final ObjectNode tasks;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            this.mapper = mapper;
            this.parent = parent;
            this.project = mapper.createObjectNode();
            this.tasks = mapper.createObjectNode();
        }

        @Override
        public void format(GradleProjectAccumulator model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            project.put("totalNumberOfBuilds", model.totalNumberOfBuilds);
            project.put("buildTime", formatMillis(model.totalBuildTime, shouldFormatDuration));
            project.put("numOfUsers", model.userNames.size());
            project.put("totalTaskExecutionTime", formatMillis(model.totalTaskExecutionTime, shouldFormatDuration));
            project.put("totalTaskAvoidanceTime", formatMillis(model.totalTaskAvoidanceTime, shouldFormatDuration));
            project.put("totalTaskAvoidableTime", formatMillis(model.totalTaskAvoidableTime, shouldFormatDuration));
            project.put("totalTaskNonAvoidableTime", formatMillis(model.totalTaskNonAvoidableTime, shouldFormatDuration));
            parent.set(model.projectName, project);
            project.set("tasks", tasks);

            applyTransforms(transforms, TaskTypeAccumulator.class, model.getTaskTypeAccumulatorMap().values().stream())
                .forEach(task -> task.getFormatter(mapper, tasks).formatSafe(task, shouldFormatDuration, transforms));
        }
    }

    @Override
    public String toString() {
        return "GradleProjectAccumulator{" +
            "projectName='" + projectName + '\'' +
            ", totalNumberOfBuilds=" + totalNumberOfBuilds +
            ", userNames=" + userNames +
            ", totalBuildTime=" + totalBuildTime +
            ", totalTaskExecutionTime=" + totalTaskExecutionTime +
            ", totalTaskAvoidanceTime=" + totalTaskAvoidanceTime +
            ", totalTaskAvoidableTime=" + totalTaskAvoidableTime +
            ", totalTaskNonAvoidableTime=" + totalTaskNonAvoidableTime +
            ", taskTypeAccumulatorMap=" + taskTypeAccumulatorMap +
            ", createTaskTypeAccumulator=" + createTaskTypeAccumulator +
            '}';
    }
}
