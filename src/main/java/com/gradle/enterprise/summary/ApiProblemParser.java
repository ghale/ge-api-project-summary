package com.gradle.enterprise.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gradle.enterprise.api.client.ApiException;
import com.gradle.enterprise.api.model.ApiProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class ApiProblemParser {
    private static final Logger logger = LoggerFactory.getLogger(ApiProblemParser.class);

    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTENT_TYPE = "application/problem+json";

    public static Optional<ApiProblem> maybeParse(ApiException apiException) {
        return apiException.getResponseHeaders()
            .firstValue("content-type")
            .filter(v -> v.startsWith(CONTENT_TYPE))
            .map(__ -> {
                try {
                    return OBJECT_MAPPER.readValue(apiException.getResponseBody(), ApiProblem.class);
                } catch (JsonProcessingException e) {
                    logger.error("Error parsing ApiException {}", apiException, e);
                    throw new RuntimeException(e);
                }
            });
    }

}
