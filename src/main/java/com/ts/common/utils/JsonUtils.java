/*
 * Description: Jackson-backed JSON serialisation/deserialisation utilities with fail-safe defaults
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
/**
 * Utility class wrapping Jackson {@link com.fasterxml.jackson.databind.ObjectMapper}
 * for JSON serialisation and deserialisation.
 *
 * <p>All methods return sensible defaults ({@code "{}"} or {@code null}) rather
 * than propagating exceptions, making them safe for use in logging paths where
 * a serialisation failure must not mask the original exception.
 */
public class JsonUtils {
    private static final Logger logger = LogManager.getLogger(JsonUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Serializes an object to a JSON string
     *
     * @param object the object to serialize
     * @return JSON string representation, or "{}" on error
     */
    public static String toJson(Object object) {
        if (object == null) {
            return "{}";
        }

        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to JSON: " + e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * Deserializes a JSON string into an object of the specified type
     *
     * @param json  the JSON string to deserialize
     * @param clazz the target class type
     * @param <T>   the target type
     * @return deserialized object of type T, or null on error
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            return mapper.readValue(json, clazz);
        } catch (IOException e) {
            logger.error("Error converting JSON to object: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Deserializes a JSON string into a Map<String, Object>
     *
     * @param json the JSON string to deserialize
     * @return deserialized Map, or an empty Map on error
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }

        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            logger.error("Error converting JSON to Map: " + e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Converts a JSON string to the specified object type
     *
     * @param <T>   The type of object to convert to
     * @param json  JSON string to convert
     * @param clazz Class representing the object type
     * @return Object of type T or null if conversion fails
     */
    public static <T> T jsonToObject(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            return mapper.readValue(json, clazz);
        } catch (IOException e) {
            logger.error("Error converting JSON to object of type: {}", clazz.getName(), e);
            return null;
        }
    }

    /**
     * Pretty print JSON string
     *
     * @param json JSON string to format
     * @return Formatted JSON string or the original string if formatting fails
     */
    public static String prettyPrint(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            Object jsonObj = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);
        } catch (IOException e) {
            logger.error("Error formatting JSON", e);
            return json;
        }
    }

}