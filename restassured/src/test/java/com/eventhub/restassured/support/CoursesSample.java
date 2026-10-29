package com.eventhub.restassured.support;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/** Loads the sample response (data/courses-sample.json) and builds one-field changes to it, addressed by JSON Pointer. */
public final class CoursesSample {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE = "/data/courses-sample.json";

    private CoursesSample() {
    }

    /** A fresh, mutable copy of the sample on every call. */
    public static ObjectNode load() {
        try (InputStream in = CoursesSample.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            return (ObjectNode) MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String json() {
        return toJson(load());
    }

    public static String toJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes the field at {@code pointer}, e.g. "/courses/api/0/price". */
    public static Consumer<ObjectNode> remove(String pointer) {
        return doc -> parentOf(doc, pointer).remove(lastField(pointer));
    }

    /** Sets (or adds) the field at {@code pointer}; {@code value} may be any Java value, including null, a List or a Map. */
    public static Consumer<ObjectNode> set(String pointer, Object value) {
        return doc -> parentOf(doc, pointer).set(lastField(pointer),
                value == null ? NullNode.getInstance() : MAPPER.valueToTree(value));
    }

    private static ObjectNode parentOf(ObjectNode doc, String pointer) {
        JsonNode parent = doc.at(JsonPointer.compile(pointer).head());
        if (!(parent instanceof ObjectNode)) throw new IllegalArgumentException("No object holds " + pointer);
        return (ObjectNode) parent;
    }

    private static String lastField(String pointer) {
        return JsonPointer.compile(pointer).last().getMatchingProperty();
    }
}
