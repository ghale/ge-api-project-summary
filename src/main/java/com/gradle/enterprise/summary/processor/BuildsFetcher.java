package com.gradle.enterprise.summary.processor;

import com.gradle.enterprise.api.GradleEnterpriseApi;
import com.gradle.enterprise.api.client.ApiException;
import com.gradle.enterprise.api.model.ApiProblem;
import com.gradle.enterprise.api.model.Build;
import com.gradle.enterprise.api.model.BuildsQuery;
import com.gradle.enterprise.summary.ApiProblemParser;
import com.gradle.enterprise.summary.InstantUtils;
import com.gradle.enterprise.summary.metrics.ErrorAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.gradle.enterprise.summary.metrics.MetricsAccumulator.FORMATTER;

public final class BuildsFetcher {
    private Logger logger = LoggerFactory.getLogger(BuildsFetcher.class);
    private static final Integer MAX_BUILDS = Integer.MAX_VALUE;
    private final GradleEnterpriseApi api;
    private final ErrorAccumulator errorAccumulator;

    public BuildsFetcher(GradleEnterpriseApi api, ErrorAccumulator errorAccumulator) {
        this.api = api;
        this.errorAccumulator = errorAccumulator;
    }

    public BlockingQueue<Build> fetchBuilds(Instant since, Instant endTime) {
        final BlockingQueue<Build> buildsQueue = new LinkedBlockingQueue<>(MAX_BUILDS);

        System.out.println("Fetching builds ...");
        Consumer<BuildsQuery> sinceApplicator = q -> q.since(since.toEpochMilli());

        try {
            AtomicBoolean finished = new AtomicBoolean(false);
            while (!finished.get()) {
                List<Build> builds = requestBuilds(sinceApplicator);

                if (!builds.isEmpty()) {
                    builds.forEach(build -> {
                        if (buildsQueue.size() >= MAX_BUILDS) {
                            finished.set(true);
                            System.out.printf("Fetched the maximum number of %d supported builds.", MAX_BUILDS);
                        } else {
                            buildsQueue.add(build);

                            if (buildsQueue.size() % 250 == 0) {
                                System.out.printf("Fetching | %5d builds queued | Currently fetching: %s \n",
                                    buildsQueue.size(),
                                    FORMATTER.format(InstantUtils.fromUTC(build.getAvailableAt()))
                                );
                            }
                        }

                        if (InstantUtils.fromUTC(build.getAvailableAt()).isAfter(endTime)) {
                            finished.set(true);
                        }
                    });
                    sinceApplicator = q -> q.sinceBuild(builds.get(builds.size() - 1).getId());
                } else {
                    finished.set(true);
                }
            }
        } catch (Exception ex) {
            logger.error("Something went wrong while fetching builds.", ex);
            System.err.printf("Something went wrong while fetching builds. Will process the %d builds already fetched.\n", buildsQueue.size());
        }
        System.out.println("Done fetching builds.");

        return buildsQueue;
    }

    private List<Build> requestBuilds(Consumer<BuildsQuery> sinceApplicator) throws ApiException {
        var query = new BuildsQuery();
        query.setMaxWaitSecs(20);
        query.setMaxBuilds(1000);
        sinceApplicator.accept(query);

        int retries = 0;

        while (true) {
            try {
                return api.getBuilds(query);
            } catch (ApiException e) {
                errorAccumulator.addError(new ErrorAccumulator.Error("FETCHING_BUILDS_" + InstantUtils.nowUTC(), e.getCode(), e.getResponseBody(), e.getMessage()));

                if (retries >= 2 || e.getCode() == 500) {
                    throw e;
                }

                Optional<ApiProblem> maybeApiProblem = ApiProblemParser.maybeParse(e);
                if (maybeApiProblem.isPresent()) {

                    // Do not retry unexpected errors
                    if (maybeApiProblem.get().getType().equals("urn:gradle:enterprise:api:problems:unexpected-error")) {
                        throw e;
                    }
                }

                System.out.println("Fetching | Error while fetching builds, retrying...");
            }
            retries++;
        }
    }
}
