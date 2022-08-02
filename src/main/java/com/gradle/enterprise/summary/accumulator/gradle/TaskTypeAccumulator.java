package com.gradle.enterprise.summary.accumulator.gradle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.GradleBuildCachePerformanceTaskExecutionEntry;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.accumulator.WorkUnitAccumulator;
import com.gradle.enterprise.summary.formatting.Formatter;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;


public class TaskTypeAccumulator extends TaskAccumulator implements WorkUnitAccumulator<GradleBuildCachePerformanceTaskExecutionEntry, TaskTypeAccumulator> {

    private final Map<String, TaskNameAccumulator> taskNameAccumulatorMap = new LinkedHashMap<>();
    private final Function<String, TaskNameAccumulator> createTaskNameAccumulator = TaskNameAccumulator::new;

    public TaskTypeAccumulator(String accumulatorName) {
        super(accumulatorName);
    }

    @Override
    public void addExecution(GradleBuildCachePerformanceTaskExecutionEntry item, String buildId) {
        super.addExecution(item, buildId);

        var split = item.getTaskPath().split(":");
        var name = split[split.length - 1];

        taskNameAccumulatorMap.computeIfAbsent(name, createTaskNameAccumulator).addExecution(item, buildId);
    }

    @Override
    public Formatter<TaskTypeAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter(mapper, parent);
    }

    public static class JsonFormatter extends TaskAccumulator.JsonFormatter<TaskTypeAccumulator> {
        private final ObjectNode tasksByName;
        private final ObjectMapper mapper;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            super(mapper, parent);
            this.mapper = mapper;
            this.tasksByName = mapper.createObjectNode();
        }

        @Override
        public void format(TaskTypeAccumulator model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            super.format(model, shouldFormatDuration, transforms);

            taskSummary.set("tasksByName", tasksByName);

            applyTransforms(transforms, TaskNameAccumulator.class, model.taskNameAccumulatorMap.values().stream())
                .forEach(task -> task.getFormatter(mapper, tasksByName).formatSafe(task, shouldFormatDuration, transforms));
        }
    }
}
