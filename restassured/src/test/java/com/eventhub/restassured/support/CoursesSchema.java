package com.eventhub.restassured.support;

import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * The courses schema, plus a way to see <em>which</em> rules a document breaks.
 * Rest Assured's matcher only says valid/invalid; this uses the same validator underneath to name the failing keywords.
 */
public final class CoursesSchema {
    /** Classpath location, for {@code matchesJsonSchemaInClasspath}. */
    public static final String CLASSPATH = "schemas/courses-schema.json";

    private static final JsonSchema SCHEMA = load();

    private CoursesSchema() {
    }

    private static JsonSchema load() {
        try {
            return JsonSchemaFactory.byDefault().getJsonSchema(JsonLoader.fromResource("/" + CLASSPATH));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + CLASSPATH, e);
        }
    }

    /** Schema keywords the document breaks (required, type, pattern, ...); empty when it is valid. */
    public static Set<String> failedKeywords(String json) {
        ProcessingReport report;
        try {
            report = SCHEMA.validateUnchecked(JsonLoader.fromString(json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> keywords = new TreeSet<>();
        for (ProcessingMessage message : report) {
            if (message.getLogLevel().compareTo(LogLevel.ERROR) >= 0) {
                keywords.add(message.asJson().path("keyword").asText());
            }
        }
        return keywords;
    }
}
