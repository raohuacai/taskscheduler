/*
 * Description: Parameter map reading, validation, and {variable} substitution utilities
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import com.ts.common.constants.Constants;
import com.ts.exception.TaskExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for reading, validating, and performing {@code {variable}}
 * substitution on task parameter maps.
 *
 * <p>Variable substitution supports the well-known placeholders
 * ({@code {workflowId}}, {@code {taskId}}, {@code {workflowInstanceId}}, etc.)
 * and falls back to a direct parameter-map lookup for unknown names.
 * tree nodes recursively so that nested JSON bodies can contain placeholders.
 */
public class ParamUtils {
    private static final Logger logger = LogManager.getLogger(ParamUtils.class);
    // Regex pattern matching {variableName} placeholders for substitution
    //private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    //private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");
    // 修正后：{ 前面不能有 $ 符号
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("(?<![$])\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    // Improved regex to allow any characters inside {{...}}, including '}'
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");

    /**
     * Returns the parameter.
     *
     * @param parameters parameter map
     * @param name       name name
     * @return the result as a String
     */
    public static String getParameter(Map<String, Object> parameters, String name) {
        if (parameters == null) {
            return "";
        }
        Object value = parameters.get(name);
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    /**
     * Returns the parameter.
     *
     * @param parameters   parameter map
     * @param name         name name
     * @param defaultValue defaultValue value
     * @return the result as a String
     */
    public static String getParameter(Map<String, Object> parameters, String name, String defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Returns the int parameter.
     *
     * @param parameters   parameter map
     * @param name         name name
     * @param defaultValue defaultValue value
     * @return the numeric result
     */
    public static int getIntParameter(Map<String, Object> parameters, String name, int defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns the boolean parameter.
     *
     * @param parameters   parameter map
     * @param name         name name
     * @param defaultValue defaultValue value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public static boolean getBooleanParameter(Map<String, Object> parameters, String name, boolean defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(str);
    }

    /**
     * Returns the required parameter.
     *
     * @param parameters     parameter map
     * @param name           name name
     * @param taskId         identifier
     * @param taskInstanceId instance identifier
     * @return the result as a String
     * @throws TaskExecutionException if an error occurs
     */
    public static String getRequiredParameter(Map<String, Object> parameters, String name, String taskId, String taskInstanceId) throws TaskExecutionException {
        if (parameters == null) {
            throw new TaskExecutionException("Parameters map is null", null, taskId, taskInstanceId);
        }
        Object value = parameters.get(name);
        if (value == null) {
            throw new TaskExecutionException("Missing required parameter: " + name, null, taskId, taskInstanceId);
        }
        String strValue = value.toString();
        if (strValue.trim().isEmpty()) {
            throw new TaskExecutionException("Required parameter is empty: " + name, null, taskId, taskInstanceId);
        }
        return strValue;
    }

    /**
     * Returns the timeout.
     *
     * @param parameters     parameter map
     * @param name           name name
     * @param defaultTimeout date/time value
     * @return the numeric result
     */
    public static int getTimeout(Map<String, Object> parameters, String name, int defaultTimeout) {
        int timeout = defaultTimeout;
        String timeoutStr = getParameter(parameters, Constants.PARAM_TASK_TIMEOUT, String.valueOf(defaultTimeout));
        try {
            timeout = Integer.parseInt(timeoutStr);
            if (timeout <= 0) {
                timeout = defaultTimeout;
                logger.warn("Invalid timeout value: {}. Using default: {}", timeoutStr, defaultTimeout);
            }
        } catch (NumberFormatException e) {
            timeout = defaultTimeout;
            logger.warn("Invalid timeout format: {}. Using default: {}", timeoutStr, defaultTimeout);
        }
        return timeout;
    }

    /**
     * Returns the lock name.
     *
     * @param parameters parameter map
     * @return the result as a String
     */
    public static String getLockName(Map<String, Object> parameters) {
        // Retrieve the lock name parameter and apply variable substitution; default is {workflowName}({workflowId})
        String lockName = getParameter(parameters, Constants.LOCK_NAME, "{workflowName}({workflowId})");
        lockName = replaceVariablesOnePass(lockName, parameters);
        return lockName;
    }

    /**
     * Returns the lock owner.
     *
     * @param parameters parameter map
     * @return the result as a String
     */
    public static String getLockOwner(Map<String, Object> parameters) {
        // Retrieve the lock owner parameter and apply variable substitution; default is {workflowInstanceId}
        String lockOwner = ParamUtils.getParameter(parameters, Constants.LOCK_OWNER, "{workflowInstanceId}");
        lockOwner = replaceVariablesOnePass(lockOwner, parameters);
        return lockOwner;
    }

    /*public static String replaceVariablesOnePass(String input, Map<String, Object> parameters) {
        if (input == null || !input.contains("{")) return input;
        return substituteSegment(input, parameters);
    }*/

    /**
     * Segment-based processing to completely eliminate the placeholder mechanism:
     * - Use ESCAPE_PATTERN to split the input into alternating "escape segments" and "normal segments".
     * - Normal segments undergo variable substitution; escape segments directly output {content}.
     * - No placeholder, thus naturally Bug 6-free.
     */
    public static String replaceVariablesOnePass(String input, Map<String, Object> parameters) {
        if (input == null || !input.contains("{")) return input;

        Matcher em = ESCAPE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (em.find()) {
            // Normal segment: text before the escape region, perform variable substitution
            if (em.start() > lastEnd) {
                result.append(substituteSegment(input.substring(lastEnd, em.start()), parameters));
            }
            // Escape segment: directly output {raw content}, no variable substitution
            result.append('{').append(em.group(1)).append('}');
            lastEnd = em.end();
        }

        // Trailing normal segment
        if (lastEnd < input.length()) {
            result.append(substituteSegment(input.substring(lastEnd), parameters));
        }

        return result.toString();
    }

    /**
     * Perform variable substitution on a normal segment that does not contain {{ }}
     */
    private static String substituteSegment(String segment, Map<String, Object> parameters) {
        if (segment.isEmpty() || !segment.contains("{")) return segment;
        Matcher vm = VARIABLE_PATTERN.matcher(segment);
        StringBuffer sb = new StringBuffer();
        while (vm.find()) {
            String varName = vm.group(1).trim();
            String paramKey = getParameterNameForVariable(varName);
            Object value = parameters.get(paramKey);
            if (value != null)
                vm.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
            else
                vm.appendReplacement(sb, Matcher.quoteReplacement(vm.group(0)));
        }
        vm.appendTail(sb);
        return sb.toString();
    }

    /**
     * Perform a one-time substitution on the entire parameter Map, using a true snapshot as the lookup source
     */
    /*public static Map<String, Object> replaceAllVariables(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) return parameters;
        boolean hasPlaceholder = parameters.values().stream()
                .anyMatch(v -> v instanceof String && ((String) v).contains("{"));
        if (!hasPlaceholder) return parameters;

        // ★ True shallow copy snapshot, not a view
        Map<String, Object> source = new LinkedHashMap<>(parameters);

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() instanceof String) {
                entry.setValue(replaceVariablesOnePass((String) entry.getValue(), source));
            }
        }
        return parameters;
    }*/
    public static Map<String, Object> replaceAllVariables(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) return parameters;
        Map<String, Object> source = new LinkedHashMap<>(parameters);
        Object replaced = resolveJsonNode(parameters, source);
        parameters.clear();
        if (replaced instanceof Map) {
            parameters.putAll((Map<String, Object>) replaced);
        }
        return parameters;
    }

    /**
     * Maps a placeholder variable name to its corresponding parameter map key
     *
     * @param variableName the placeholder variable name (without braces)
     * @return the corresponding parameter map key, or the variable name itself if not mapped
     */
    private static String getParameterNameForVariable(String variableName) {
        switch (variableName.toLowerCase()) {
            case "workflowid":
                return Constants.PARAM_WORKFLOW_ID;
            case "taskid":
                return Constants.PARAM_TASK_ID;
            case "workflowname":
                return Constants.PARAM_WORKFLOW_NAME;
            case "taskname":
                return Constants.PARAM_TASK_NAME;
            case "workflowinstanceid":
                return Constants.PARAM_WORKFLOW_INSTANCE;
            case "taskinstanceid":
                return Constants.PARAM_TASK_INSTANCE;
            case "topparentworkflowinstance":
                return Constants.PARAM_TOP_PARENT_WORKFLOW_INSTANCE;
            default:
                return variableName; // Fall through: use the variable name directly as a parameter key
        }
    }

    /**
     * Recursively walk a parsed JSON node and replace {key} placeholders
     * in every String leaf value.
     * <p>
     * Node types after parsing:
     * String  -> apply replaceVariables()
     * Map     -> recurse into each value
     * List    -> recurse into each element
     * Number / Boolean / null -> return as-is
     */
    @SuppressWarnings("unchecked")
    private static Object resolveJsonNode(Object node, Map<String, Object> parameters) {
        if (node instanceof String) {
            return replaceVariablesOnePass((String) node, parameters);
        }
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k, resolveJsonNode(v, parameters)));
            return result;
        }
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            List<Object> result = new java.util.ArrayList<>();
            list.forEach(item -> result.add(resolveJsonNode(item, parameters)));
            return result;
        }
        // Number, Boolean, null – return unchanged
        return node;
    }

    public static String likePattern(String value) {
        return (value != null && !value.isEmpty()) ? "%" + value + "%" : value;
    }

    private static Object gsonElementToJava(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive p = element.getAsJsonPrimitive();
            if (p.isString()) return p.getAsString();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsNumber();
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            element.getAsJsonObject().entrySet()
                    .forEach(e -> map.put(e.getKey(), gsonElementToJava(e.getValue())));
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new java.util.ArrayList<>();
            element.getAsJsonArray().forEach(e -> list.add(gsonElementToJava(e)));
            return list;
        }
        return element.toString();
    }

}