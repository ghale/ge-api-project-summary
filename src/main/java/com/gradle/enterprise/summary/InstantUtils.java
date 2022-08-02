package com.gradle.enterprise.summary;

import java.time.Instant;
import java.time.ZoneId;

public class InstantUtils {

    public static Instant nowUTC() {
        return Instant.now().atZone(ZoneId.of("UTC")).toInstant();
    }

    public static Instant fromUTC(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC")).toInstant();
    }

    public static String toString(Instant date) {
        return date.toString()
            .replace("T", "_")
            .replace("Z", "")
            .replace(":", "-")
            .replace(".", "-");
    }
}
