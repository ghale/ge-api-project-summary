package com.gradle.enterprise.summary.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsAccumulator {
    private Logger logger = LoggerFactory.getLogger(MetricsAccumulator.class);
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.from(ZoneOffset.UTC));

    private final AtomicLong failedApiCalls = new AtomicLong(0);
    private final AtomicLong lastAvailableAt = new AtomicLong(-1);
    private final AtomicLong totalBuildsProcessed = new AtomicLong(0);
    private final long initialNrOfBuilds;

    private Instant processingStartTime;

    public MetricsAccumulator(long initialNrOfBuilds) {
        this.initialNrOfBuilds = initialNrOfBuilds;
    }

    public void startProcessing() {
        processingStartTime = Instant.now();
    }

    public void markBuildProcessedSuccessfully(Long buildAvailableAt) {
        markBuild(buildAvailableAt);
    }

    public void markBuildProcessedWithError(Long buildAvailableAt) {
        failedApiCalls.getAndIncrement();

        markBuild(buildAvailableAt);
    }

    private void markBuild(Long buildAvailableAt) {
        if (totalBuildsProcessed.incrementAndGet() % 100 == 0 && totalBuildsProcessed.get() > 0) {
            if (lastAvailableAt.get() < buildAvailableAt) {
                lastAvailableAt.set(buildAvailableAt);
            }

            printMetrics();
        }
    }

    public synchronized void printMetrics() {
        try {
            long secondsLeft = estimateTimeLeft();
            long buildsPerSec = estimateThroughput();
            long progressPercent = estimateProgress();
            Thread.sleep(1000l);
            System.out.printf("Processing | %12s builds | %3d errors | %4d builds/sec | Currently processing: %s | Progress: %2d%% | %s elapsed | %s ETA \n",
                totalBuildsProcessed.get() + "/" + initialNrOfBuilds,
                failedApiCalls.get(),
                buildsPerSec,
                FORMATTER.format(Instant.ofEpochMilli(lastAvailableAt.get())),
                progressPercent,
                DurationFormatter.formatSeconds(Duration.between(processingStartTime, Instant.now()).getSeconds()),
                DurationFormatter.formatSeconds(secondsLeft));
        } catch (Exception ex) {
            logger.error("Error printing metrics", ex);
            System.out.println("Processing ...");
        }
    }

    private long estimateThroughput() {
        if (Duration.between(processingStartTime, Instant.now()).getSeconds() == 0) {
            return -1;
        }

        return totalBuildsProcessed.get() / Duration.between(processingStartTime, Instant.now()).getSeconds();
    }

    private long estimateTimeLeft() {
        long throughput = estimateThroughput();

        if (throughput < 0) {
            return -1;
        }

        return (initialNrOfBuilds - totalBuildsProcessed.get()) / throughput;
    }

    private long estimateProgress() {
        return totalBuildsProcessed.get() * 100 / initialNrOfBuilds;
    }

}
