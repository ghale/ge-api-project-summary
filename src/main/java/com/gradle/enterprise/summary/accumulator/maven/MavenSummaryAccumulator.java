package com.gradle.enterprise.summary.accumulator.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.api.model.Build;
import com.gradle.enterprise.api.model.MavenAttributes;
import com.gradle.enterprise.api.model.MavenBuildCachePerformance;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.accumulator.SummaryAccumulator;
import com.gradle.enterprise.summary.formatting.Formatter;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MavenSummaryAccumulator implements SummaryAccumulator<MavenAttributes, MavenBuildCachePerformance, MavenSummaryAccumulator> {

    private int numberOfBuilds;

    private int numberOfFailedBuilds;
    private final Set<String> users = new HashSet<>();
    private final Set<String> projects = new HashSet<>();
    private long totalBuildTime = 0L;

    private final Set<String> ciBuildIds = new HashSet<>();
    private final Set<String> localBuildIds = new HashSet<>();

    private final Map<String, MavenProjectAccumulator> projectAccumulatorMap = new LinkedHashMap<>();

    private final Function<String, MavenProjectAccumulator> createProjectAccumulator;

    public MavenSummaryAccumulator() {
        this.createProjectAccumulator = MavenProjectAccumulator::new;
    }

    public int getNumberOfBuilds() {
        return numberOfBuilds;
    }

    public Map<String, MavenProjectAccumulator> getProjectAccumulatorMap() {
        return projectAccumulatorMap;
    }

    @Override
    public synchronized void addBuild(Build build, MavenAttributes attributes, MavenBuildCachePerformance model) {
        numberOfBuilds++;
        if (attributes.getHasFailed()) {
            numberOfFailedBuilds++;
        }

        users.add(attributes.getEnvironment().getUsername());
        projects.add(attributes.getTopLevelProjectName());
        totalBuildTime += attributes.getBuildDuration();
        if (attributes.getTags().contains("CI")) {
            ciBuildIds.add(build.getId());
        } else {
            localBuildIds.add(build.getId());
        }

        addToProjectAccumulator(build, attributes, model);
    }

    private void addToProjectAccumulator(Build build, MavenAttributes attributes, MavenBuildCachePerformance model) {
        var projectName = attributes.getTopLevelProjectName();

        if (projectName == null) {
            projectName = "(N/A)";
        }

        projectAccumulatorMap.computeIfAbsent(projectName, createProjectAccumulator)
            .addBuild(build, attributes, model);
    }

    @Override
    public Formatter<MavenSummaryAccumulator> getFormatter(ObjectMapper mapper, ObjectNode parent) {
        return new JsonFormatter(mapper, parent);
    }

    public static class JsonFormatter implements Formatter<MavenSummaryAccumulator> {
        private final ObjectMapper mapper;
        private final ObjectNode parent;
        private final ObjectNode projects;

        public JsonFormatter(ObjectMapper mapper, ObjectNode parent) {
            this.mapper = mapper;
            this.parent = parent;
            this.projects = mapper.createObjectNode();
        }

        @Override
        public void format(MavenSummaryAccumulator model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
            ObjectNode summary = mapper.createObjectNode();

            summary.put("totalNumberOfBuilds", model.numberOfBuilds);
            summary.put("totalBuildTime", formatMillis(model.totalBuildTime, shouldFormatDuration));
            summary.put("totalNumberOfUsers", model.users.size());
            summary.put("totalNumberOfProjects", model.projects.size());
            parent.set("summary", summary);
            parent.set("projects", projects);

            applyTransforms(transforms, MavenProjectAccumulator.class, model.getProjectAccumulatorMap().values().stream())
                .forEach(project -> project.getFormatter(mapper, projects).formatSafe(project, shouldFormatDuration, transforms));
        }
    }

    @Override
    public String toString() {
        return "MavenSummaryAccumulator{" +
            "numberOfBuilds=" + numberOfBuilds +
            ", numberOfFailedBuilds=" + numberOfFailedBuilds +
            ", users=" + users +
            ", projects=" + projects +
            ", totalBuildTime=" + totalBuildTime +
            ", ciBuildIds=" + ciBuildIds +
            ", localBuildIds=" + localBuildIds +
            ", projectAccumulatorMap=" + projectAccumulatorMap +
            ", createProjectAccumulator=" + createProjectAccumulator +
            '}';
    }
}
