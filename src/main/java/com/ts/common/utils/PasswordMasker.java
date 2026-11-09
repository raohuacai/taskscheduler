/*
 * Description: Length-preserving masking of password values in JSON parameter payloads
 * Author: Rock Rao
 * Date: 10/17/2026
 * Version: 1.0
 */
package com.ts.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * Masks secret values in JSON parameter payloads before they are returned to a
 * caller.
 *
 * <p>A key is treated as a secret when it contains {@code "password"}
 * (case-insensitive), which covers every credential key used by this project:
 * {@code password}, {@code sourcePassword}, {@code targetPassword},
 * {@code ssh.password}, {@code httpAuthPassword} and {@code srePassword}.
 *
 * <p>Each character of a masked value is replaced by exactly one {@code '*'},
 * so the value length - and therefore the overall document layout - is
 * unchanged: {@code "password":"Abcd1234"} becomes
 * {@code "password":"********"}.
 *
 * <p>Only string values are masked; numbers, booleans and null are left as-is
 * because a password is always a string. Masking is recursive, so nested
 * objects and arrays are handled.
 */
public final class PasswordMasker {

    /**
     * Re-serialises the masked document. HTML escaping is disabled so that
     * ordinary parameter values (jdbcUrl contains '&' and '=', SQL contains
     * '&lt;') are not rewritten into unicode escapes - the payload stays as
     * close to the original as possible.
     */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private PasswordMasker() { }

    /**
     * Returns true when a parameter key denotes a secret.
     * Rule: the key contains "password" (case-insensitive).
     *
     * @param key parameter name
     * @return {@code true} if the value of this key must be masked
     */
    public static boolean isSecretKey(String key) {
        return key != null && key.toLowerCase().contains("password");
    }

    /**
     * Returns {@code n} asterisks, or an empty string when {@code n <= 0}.
     *
     * @param n number of characters to replace
     * @return the mask string
     */
    public static String stars(int n) {
        return n <= 0 ? "" : "*".repeat(n);
    }

    /**
     * Masks every value whose key contains "password" in a JSON document.
     *
     * <p>Never throws: {@code null}, blank and non-JSON input are returned
     * unchanged so that a malformed parameter payload cannot break the caller.
     *
     * @param json JSON text (typically a {@code TaskParameter.pvalue})
     * @return the masked JSON, or the input unchanged when it is not JSON
     */
    public static String maskJson(String json) {
        if (json == null || json.isBlank()) return json;

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (Exception e) {
            // Not valid JSON - nothing to walk, hand the value back untouched
            return json;
        }
        return GSON.toJson(maskElement(parsed));
    }

    /**
     * Recursively rebuilds a JSON element, masking secret values.
     */
    private static JsonElement maskElement(JsonElement element) {
        if (element == null) return null;

        if (element.isJsonObject()) {
            JsonObject masked = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();

                if (isSecretKey(key) && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    masked.addProperty(key, stars(value.getAsString().length()));
                } else {
                    masked.add(key, maskElement(value));
                }
            }
            return masked;
        }

        if (element.isJsonArray()) {
            JsonArray masked = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                masked.add(maskElement(item));
            }
            return masked;
        }

        return element;
    }
}
