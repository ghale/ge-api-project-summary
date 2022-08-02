package com.gradle.enterprise.summary.metrics;

import org.apache.commons.lang3.time.DurationFormatUtils;

public class DurationFormatter {

    public static String formatSeconds(long seconds) {
        return seconds < 0 ? "-" : "" + DurationFormatUtils.formatDuration(Math.abs(seconds) * 1000, "HH:mm:ss");
    }

    public static String formatMillis(long millis) {
        return millis < 0 ? "-" : "" + DurationFormatUtils.formatDuration(Math.abs(millis), "HH:mm:ss");
    }

}
