package com.gradle.enterprise.summary.accumulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gradle.enterprise.summary.formatting.Formatter;

public interface Accumulator<T> {

    Formatter<T> getFormatter(ObjectMapper mapper, ObjectNode parent);

}
