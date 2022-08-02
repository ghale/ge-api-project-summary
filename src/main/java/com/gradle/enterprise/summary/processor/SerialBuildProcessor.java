package com.gradle.enterprise.summary.processor;

import com.gradle.enterprise.api.GradleEnterpriseApi;
import com.gradle.enterprise.api.client.ApiException;
import com.gradle.enterprise.api.model.Build;
import com.gradle.enterprise.api.model.BuildAttributesValue;
import com.gradle.enterprise.api.model.BuildQuery;
import com.gradle.enterprise.summary.ApiProblemParser;
import com.gradle.enterprise.summary.InstantUtils;
import com.gradle.enterprise.summary.accumulator.gradle.GradleSummaryAccumulator;
import com.gradle.enterprise.summary.accumulator.maven.MavenSummaryAccumulator;
import com.gradle.enterprise.summary.metrics.ErrorAccumulator;
import com.gradle.enterprise.summary.metrics.MetricsAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class SerialBuildProcessor implements BuildProcessor {
    private final Logger logger = LoggerFactory.getLogger(SerialBuildProcessor.class);
    private final GradleEnterpriseApi api;
    private final String serverUrl;
    private final String projectName;
    private final GradleSummaryAccumulator gradleSummaryAccumulator;
    private final MavenSummaryAccumulator mavenSummaryAccumulator;
    private final MetricsAccumulator metricsAccumulator;
    private final ErrorAccumulator errorAccumulator;

    public SerialBuildProcessor(GradleEnterpriseApi api,
                                String serverUrl,
                                String projectName,
                                GradleSummaryAccumulator summaryAccumulator,
                                MavenSummaryAccumulator mavenSummaryAccumulator,
                                MetricsAccumulator metricsAccumulator,
                                ErrorAccumulator errorAccumulator) {
        this.api = api;
        this.serverUrl = serverUrl;
        this.projectName = projectName;
        this.gradleSummaryAccumulator = summaryAccumulator;
        this.mavenSummaryAccumulator = mavenSummaryAccumulator;
        this.metricsAccumulator = metricsAccumulator;
        this.errorAccumulator = errorAccumulator;
    }

    @Override
    public void process(Build build) {
        try {
            if (build.getBuildToolType().equalsIgnoreCase("gradle")) {
                processGradleBuild(build);
            } else if (build.getBuildToolType().equalsIgnoreCase("maven")) {
                processMavenBuild(build);
            }

            metricsAccumulator.markBuildProcessedSuccessfully(build.getAvailableAt());
        } catch (Exception e) {
            logger.error("Error processing {}", build.getId(), e);
            metricsAccumulator.markBuildProcessedWithError(build.getAvailableAt());
            if (e instanceof ApiException && !e.getMessage().contains("GOAWAY")) {
                errorAccumulator.addError(new ErrorAccumulator.Error(build.getId(), ((ApiException) e).getCode(), ((ApiException) e).getResponseBody(), e.getMessage()));
            }
//            reportError(build, e);
        }
    }

    private void processMavenBuild(Build build) throws ApiException {
        var attributes = api.getMavenAttributes(build.getId(), new BuildQuery());

//        if (!isMasterOrRelease(attributes.getValues())) {
//            return;
//        }

        if (projectName == null || projectName.equals(attributes.getTopLevelProjectName())) {
            var model = api.getMavenBuildCachePerformance(build.getId(), new BuildQuery());
//            reportBuild(
//                build,
//                computeCacheHitPercentage(model),
//                computeAvoidanceSavingsRatioPercentage(model),
//                attributes.getTopLevelProjectName(),
//                attributes.getBuildDuration(),
//                attributes.getEnvironment().getUsername()
//            );
            mavenSummaryAccumulator.addBuild(build, attributes, model);
        }
    }

    private void processGradleBuild(Build build) throws ApiException {
        var attributes = api.getGradleAttributes(build.getId(), new BuildQuery());

//        if (!isMasterOrRelease(attributes.getValues())) {
//            return;
//        }


        if (projectName == null || projectName.equals(attributes.getRootProjectName())) {
            var model = api.getGradleBuildCachePerformance(build.getId(), new BuildQuery());
//            reportBuild(
//                build,
//                computeCacheHitPercentage(model),
//                computeAvoidanceSavingsRatioPercentage(model),
//                attributes.getRootProjectName(),
//                attributes.getBuildDuration(),
//                attributes.getEnvironment().getUsername()
//            );
            gradleSummaryAccumulator.addBuild(build, attributes, model);
        }
    }

    private boolean isMasterOrRelease(List<BuildAttributesValue> buildAttributesValues) {
        return buildAttributesValues.stream().anyMatch(v -> v.getName().equals("Git Branch Name") && v.getValue() != null && (v.getValue().equals("release") || v.getValue().equals("master")));
    }

    private void reportBuild(Build build, BigDecimal cacheHitPercentage, BigDecimal avoidanceSavingsRatioPercentage, String rootProjectName, Long buildDuration, String username) {
        System.out.printf("Build Scan | %s | Project: %s | 🗓  %s | ⏱  %s ms\t| 👤 %s%n - \tCache hit percentage: %s%%%n - \tAvoidance savings ratio: %s%%%n%n",
            buildScanUrl(build),
            rootProjectName,
            InstantUtils.fromUTC(build.getAvailableAt()).toString(),
            buildDuration,
            username,
            cacheHitPercentage,
            avoidanceSavingsRatioPercentage
        );
    }

    private void reportError(Build build, ApiException e) {
        System.err.printf("API Error %s for Build Scan ID %s%n%s%n", e.getCode(), build.getId(), e.getResponseBody());
        if (e.getResponseBody() != null) {
            ApiProblemParser.maybeParse(e).ifPresent(apiProblem -> {
                // Types of API problems can be checked as following
                if (apiProblem.getType().equals("urn:gradle:enterprise:api:problems:build-deleted")) {
                    // Handle the case when the Build Scan is deleted.
                    System.err.println(apiProblem.getDetail());
                }
            });
        }
    }

    private URI buildScanUrl(Build build) {
        return URI.create(serverUrl + "/s/" + build.getId());
    }

}
