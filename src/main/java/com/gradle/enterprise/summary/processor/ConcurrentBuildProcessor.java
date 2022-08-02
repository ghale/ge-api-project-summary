package com.gradle.enterprise.summary.processor;

import com.gradle.enterprise.api.model.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConcurrentBuildProcessor {
    private Logger logger = LoggerFactory.getLogger(ConcurrentBuildProcessor.class);
    private final Queue<Build> buildsQueue;
    private final ThreadPoolExecutor executor;

    private static final int QUEUE_CAPACITY = 2056;

    private final BuildProcessor buildProcessor;

    public ConcurrentBuildProcessor(int numberOfThreads, BuildProcessor buildProcessor, Queue<Build> buildsQueue) {
        this.buildProcessor = buildProcessor;
        this.buildsQueue = buildsQueue;
        this.executor = new ThreadPoolExecutor(numberOfThreads, numberOfThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY));
    }

    public void processBuilds() {
        System.out.println("Processing builds ...");
        try {
            process();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Something went wrong while processing the builds.", e);
            System.out.println("Something went wrong while processing the builds.");
        } finally {
            executor.shutdownNow();
        }

        System.out.println("Done processing builds.");
    }

    private void process() throws InterruptedException {
        for (Build build : buildsQueue) {
            while (executor.getQueue().size() >= QUEUE_CAPACITY - 1) {
                Thread.sleep(100l);
            }
            executor.execute(() -> buildProcessor.process(build));
        }

        executor.shutdown();

        int waiting = 0;
        while (!executor.awaitTermination(10, TimeUnit.SECONDS) && waiting < 600) {
            waiting += 10;
            System.out.printf("Processing | %4s builds left to process\n", (executor.getActiveCount() + (executor.getQueue().size())));
        }
    }
}
