package com.gradle.enterprise.summary.metrics;

import java.util.HashSet;
import java.util.Set;

public class ErrorAccumulator {
    private static final Integer MAX_ERRORS = 100_000;
    private static final Set<Error> errors = new HashSet<>();


    public ErrorAccumulator() {
    }

    public void addError(Error error) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(error);
        }
    }

    public Set<Error> getErrors() {
        return errors;
    }

    public static class Error {
        public String responseBody;
        public String detailMessage;
        public String buildId;
        public int code;

        public Error(String buildId, int code, String responseBody, String detailMessage) {
            this.buildId = buildId;
            this.code = code;
            this.responseBody = responseBody;
            this.detailMessage = detailMessage;
        }
    }
}
