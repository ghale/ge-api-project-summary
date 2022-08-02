package com.gradle.enterprise.summary.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gradle.enterprise.summary.InstantUtils;
import com.gradle.enterprise.summary.metrics.ErrorAccumulator;

import java.io.IOException;
import java.time.Instant;

public class ErrorsWriter extends OutputWriter {
    private boolean writtenErrors = false;

    public void write(ErrorAccumulator errorAccumulator, Instant instant) {
        try {
            // This is a hack to avoid writing the errors twice because of the shutdown hook.
            if (!writtenErrors) {
                writeStringToFile("errors_" + InstantUtils.toString(instant) + ".json", new ObjectMapper().writeValueAsString(errorAccumulator.getErrors()));
                writtenErrors = true;
            }
        } catch (IOException e) {

        }
    }
}
