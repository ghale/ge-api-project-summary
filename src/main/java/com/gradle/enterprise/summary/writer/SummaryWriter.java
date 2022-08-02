package com.gradle.enterprise.summary.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.summary.InstantUtils;
import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.accumulator.SummaryAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SummaryWriter extends OutputWriter {
    private static Logger logger = LoggerFactory.getLogger(SummaryWriter.class);
    private final boolean shouldFormatDuration;
    private final List<StreamTransform<?>> transforms;

    private final Set<Class<?>> outputMap = new HashSet<>();

    public SummaryWriter(boolean shouldFormatDuration, List<StreamTransform<?>> transforms) {
        this.shouldFormatDuration = shouldFormatDuration;
        this.transforms = transforms;
    }

    public <T extends SummaryAccumulator<?, ?, T>> void write(T summaryAccumulator, Instant date, String name) {
        if (outputMap.contains(summaryAccumulator.getClass())) {
            return;
        }

        try {
            String summary = formatSummary(summaryAccumulator, transforms);
            writeStringToFile("results/" + InstantUtils.toString(date) + "/" + name + "_summary.json", summary);
            outputMap.add(summaryAccumulator.getClass());
        } catch (Exception e) {
            System.err.println("There was an error writing the output for " + name);

            logger.error("There was an error writing the output for " + name, e);
        }
    }

    private <T extends SummaryAccumulator<?, ?, T>> String formatSummary(T accumulator, List<StreamTransform<?>> transforms) throws JsonProcessingException {
        ObjectMapper mapper = JsonMapper.builder().enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS).build();
        ObjectNode root = mapper.createObjectNode();

        accumulator.getFormatter(mapper, root).formatSafe(accumulator, shouldFormatDuration, transforms);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
}
