package com.gradle.enterprise.summary;

import java.util.stream.Stream;

public abstract class StreamTransform<A> {
    private final Class<A> clazz;

    public StreamTransform(Class<A> clazz) {
        this.clazz = clazz;
    }

    public Class<A> getClazz() {
        return clazz;
    }

    @SuppressWarnings("rawtypes")
    public abstract Stream<A> transform(Stream stream);
}
