package com.gradle.enterprise.summary.formatting;

import com.gradle.enterprise.summary.StreamTransform;
import com.gradle.enterprise.summary.metrics.DurationFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

public interface Formatter<T> {
    Logger logger = LoggerFactory.getLogger(Formatter.class);

    void format(T model, boolean shouldFormatDuration, List<StreamTransform<?>> transforms);

    default void formatSafe(T model, boolean shouldFormatDate, List<StreamTransform<?>> transforms) {
        try {
            format(model, shouldFormatDate, transforms);
        } catch (Exception e) {
            logger.error("Error formatting model {}", model, e);
        }
    }

    @SuppressWarnings({"unchecked"})
    default <K> Stream<K> applyTransforms(List<StreamTransform<?>> transforms, Class<K> clazz, Stream<K> stream) {
        Stream<K> newStream = stream;

        for (StreamTransform<?> transform : transforms) {
            if (transform.getClazz().isAssignableFrom(clazz)) {
                newStream = (Stream<K>) transform.transform(stream);
            }
        }

        return newStream;
    }

    default String formatMillis(Long millis, boolean shouldFormatDate) {
        if (shouldFormatDate) {
            return DurationFormatter.formatMillis(millis);
        } else {
            return millis.toString();
        }
    }

}
