/*
 * Description: Lightweight boolean expression evaluator for time-based task scheduling conditions
 * Author: Rock Rao
 * Date: 8/7/2026
 * Version: 1.0
 */
package com.ts.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates simple boolean expressions built from calendar-derived fields, used to decide
 * whether a task should run given the current (or a supplied reference) date.
 *
 * <p>Grammar:
 * <pre>
 * expression := orExpr
 * orExpr     := andExpr ( "||" andExpr )*
 * andExpr    := unary ( "&&" unary )*
 * unary      := "!" unary | "(" expression ")" | comparison
 * comparison := FIELD ( comparator value )?
 * comparator := "=" | "==" | "!=" | ">=" | "<=" | ">" | "<" | "IN" | "NOT IN"
 * value      := NUMBER | "(" NUMBER ("," NUMBER)* ")"
 * </pre>
 *
 * <p>Numeric fields require a comparator; boolean fields may appear bare (optionally negated
 * with {@code !}).
 *
 * <h3>Supported fields</h3>
 * <ul>
 *   <li>Numeric: {@code YEAR}, {@code MONTH} (1-12), {@code QUARTER} (1-4),
 *       {@code MONTH_OF_QUARTER} (1-3), {@code DAY_OF_MONTH} (1-31),
 *       {@code DAY_OF_MONTH_FROM_END} (1 = last day of month, 2 = second-to-last, ...),
 *       {@code DAY_OF_WEEK} (ISO-8601: 1=Monday ... 7=Sunday), {@code WEEK_OF_MONTH}
 *       (1-based, ceil(dayOfMonth/7); i.e. days 1-7 = week 1, 8-14 = week 2, ...),
 *       {@code DAY_OF_QUARTER} (1-based day count since the quarter's first day)</li>
 *   <li>Boolean: {@code IS_WEEKDAY}, {@code IS_WEEKEND}, {@code IS_LAST_DAY_OF_MONTH},
 *       {@code IS_FIRST_WEEK_OF_MONTH}, {@code IS_LAST_WEEK_OF_MONTH},
 *       {@code IS_LAST_MONTH_OF_QUARTER}</li>
 * </ul>
 *
 * <p>Used by {@link com.ts.core.engine.WorkflowInstance#executeTask} to evaluate the
 * {@code taskCondition} parameter before dispatching a task; on any syntax or semantic
 * error a {@link ConditionEvaluationException} is thrown so the caller can fail closed
 * (mark the task as ERROR rather than silently running or silently skipping it).
 *
 * @see com.ts.core.engine.WorkflowInstance
 */
public final class ConditionEvaluator {
    private static final Logger logger = LogManager.getLogger(ConditionEvaluator.class);

    private ConditionEvaluator() {
    }

    /**
     * Evaluates the condition against the current date.
     *
     * @param condition  the condition expression; a blank or {@code null} condition
     *                    always evaluates to {@code true} (no restriction configured)
     * @return {@code true} if the condition is satisfied, {@code false} otherwise
     * @throws ConditionEvaluationException if the expression is malformed or references
     *         an unknown field
     */
    public static boolean evaluate(String condition) {
        return evaluate(condition, new Date());
    }

    /**
     * Evaluates the condition against a supplied reference date. Useful for backfills,
     * testing, or keeping the condition aligned with a workflow's logical run date
     * rather than wall-clock "now".
     *
     * @param condition      the condition expression; a blank or {@code null} condition
     *                        always evaluates to {@code true} (no restriction configured)
     * @param referenceDate  the date used to resolve calendar fields such as
     *                        {@code WEEK_OF_MONTH} or {@code MONTH_OF_QUARTER}
     * @return {@code true} if the condition is satisfied, {@code false} otherwise
     * @throws ConditionEvaluationException if the expression is malformed or references
     *         an unknown field
     */
    public static boolean evaluate(String condition, Date referenceDate) {
        if (condition == null || condition.trim().isEmpty()) {
            // No condition configured => task is always eligible to run
            return true;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(referenceDate);

        List<String> tokens = Lexer.tokenize(condition);
        Parser parser = new Parser(tokens, cal);
        boolean result = parser.parseExpression();
        parser.expectEnd();
        logger.debug("Condition [{}] evaluated to {} for reference date {}", condition, result, referenceDate);
        return result;
    }

    // ------------------------------------------------------------------
    // Field resolution
    // ------------------------------------------------------------------

    /** Numeric field name -> resolver function reading the value from a {@link Calendar}. */
    private static final Map<String, ToIntFunction<Calendar>> NUMERIC_FIELDS = new HashMap<>();

    /** Boolean field name -> predicate testing the value against a {@link Calendar}. */
    private static final Map<String, Predicate<Calendar>> BOOLEAN_FIELDS = new HashMap<>();

    static {
        NUMERIC_FIELDS.put("YEAR", cal -> cal.get(Calendar.YEAR));
        NUMERIC_FIELDS.put("MONTH", cal -> cal.get(Calendar.MONTH) + 1);
        NUMERIC_FIELDS.put("QUARTER", cal -> (cal.get(Calendar.MONTH) / 3) + 1);
        NUMERIC_FIELDS.put("MONTH_OF_QUARTER", cal -> (cal.get(Calendar.MONTH) % 3) + 1);
        NUMERIC_FIELDS.put("DAY_OF_MONTH", cal -> cal.get(Calendar.DAY_OF_MONTH));
        NUMERIC_FIELDS.put("DAY_OF_MONTH_FROM_END", cal ->
                cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1);
        NUMERIC_FIELDS.put("DAY_OF_WEEK", ConditionEvaluator::isoDayOfWeek);
        NUMERIC_FIELDS.put("WEEK_OF_MONTH", cal -> ((cal.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1);
        NUMERIC_FIELDS.put("DAY_OF_QUARTER", ConditionEvaluator::dayOfQuarter);

        BOOLEAN_FIELDS.put("IS_WEEKDAY", cal -> isoDayOfWeek(cal) <= 5);
        BOOLEAN_FIELDS.put("IS_WEEKEND", cal -> isoDayOfWeek(cal) >= 6);
        BOOLEAN_FIELDS.put("IS_LAST_DAY_OF_MONTH", cal ->
                cal.get(Calendar.DAY_OF_MONTH) == cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        BOOLEAN_FIELDS.put("IS_FIRST_WEEK_OF_MONTH", cal ->
                NUMERIC_FIELDS.get("WEEK_OF_MONTH").applyAsInt(cal) == 1);
        BOOLEAN_FIELDS.put("IS_LAST_WEEK_OF_MONTH", cal -> {
            int lastDayWeek = ((cal.getActualMaximum(Calendar.DAY_OF_MONTH) - 1) / 7) + 1;
            return NUMERIC_FIELDS.get("WEEK_OF_MONTH").applyAsInt(cal) == lastDayWeek;
        });
        BOOLEAN_FIELDS.put("IS_LAST_MONTH_OF_QUARTER", cal ->
                NUMERIC_FIELDS.get("MONTH_OF_QUARTER").applyAsInt(cal) == 3);
    }

    /**
     * Converts {@link Calendar}'s Sunday=1..Saturday=7 day-of-week numbering to
     * ISO-8601 Monday=1..Sunday=7, which is the more intuitive convention for
     * scheduling expressions.
     *
     * @param cal  the calendar to read
     * @return the ISO-8601 day of week (1=Monday ... 7=Sunday)
     */
    private static int isoDayOfWeek(Calendar cal) {
        int javaDow = cal.get(Calendar.DAY_OF_WEEK); // Sunday=1 .. Saturday=7
        return javaDow == Calendar.SUNDAY ? 7 : javaDow - 1;
    }

    /**
     * Computes the 1-based day number within the current quarter.
     *
     * <p>Uses {@link Calendar#DAY_OF_YEAR} rather than a raw millisecond difference:
     * subtracting {@code getTimeInMillis()} values and dividing by 24h can be off by one
     * whenever the two dates straddle a daylight-saving-time transition (the elapsed
     * wall-clock interval is then 23h or 25h, not a clean multiple of 24h). Calendar
     * fields are DST-safe since they are computed, not derived from an epoch difference.
     * A quarter never spans a year boundary, so subtracting {@code DAY_OF_YEAR} values
     * directly is always safe here.
     *
     * @param cal  the calendar to read
     * @return the 1-based day of the quarter
     */
    private static int dayOfQuarter(Calendar cal) {
        int quarterStartMonth = (cal.get(Calendar.MONTH) / 3) * 3;
        Calendar quarterStart = (Calendar) cal.clone();
        // 先设为 1 号（任何月份都有 1 号）
        quarterStart.set(Calendar.DAY_OF_MONTH, 1);
        // 再切换月份（此时日期已经是 1 号，不会进位）
        quarterStart.set(Calendar.MONTH, quarterStartMonth);
        int currentDayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        int startDayOfYear = quarterStart.get(Calendar.DAY_OF_YEAR);
        return currentDayOfYear - startDayOfYear + 1;
    }

    // ------------------------------------------------------------------
    // Lexer
    // ------------------------------------------------------------------

    /**
     * Splits a condition expression into tokens: identifiers, integers, comparators,
     * parentheses, comma, and the boolean operators {@code &&} / {@code ||} / {@code !}.
     */
    private static final class Lexer {
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "\\s*(&&|\\|\\||!=|==|>=|<=|>|<|=|!|\\(|\\)|,|[A-Za-z_][A-Za-z0-9_]*|-?\\d+)\\s*");

        /**
         * Tokenizes the input.
         *
         * @param input  the raw condition expression
         * @return the ordered list of tokens
         * @throws ConditionEvaluationException if an unrecognized character sequence is found
         */
        static List<String> tokenize(String input) {
            List<String> tokens = new ArrayList<>();
            Matcher m = TOKEN_PATTERN.matcher(input);
            int pos = 0;
            while (pos < input.length()) {
                if (!m.find(pos) || m.start() != pos) {
                    throw new ConditionEvaluationException(
                            "Unrecognized token at position " + pos + " in condition: " + input);
                }
                tokens.add(m.group(1));
                pos = m.end();
            }
            return tokens;
        }
    }

    // ------------------------------------------------------------------
    // Recursive-descent parser / evaluator
    // ------------------------------------------------------------------

    /**
     * Recursive-descent parser that evaluates the token stream directly against a
     * {@link Calendar} instance (no intermediate AST is built, since expressions are
     * short-lived and evaluated once per task submission).
     */
    private static final class Parser {
        private final List<String> tokens;
        private final Calendar cal;
        private int pos = 0;

        /**
         * Constructs a new Parser instance.
         *
         * @param tokens  the tokenized condition expression
         * @param cal     the calendar used to resolve field values
         */
        Parser(List<String> tokens, Calendar cal) {
            this.tokens = tokens;
            this.cal = cal;
        }

        /**
         * Parses and evaluates the full expression.
         *
         * @return the boolean result of the expression
         */
        boolean parseExpression() {
            return parseOr();
        }

        /**
         * Verifies that all tokens were consumed; guards against trailing garbage such
         * as an unmatched closing parenthesis.
         */
        void expectEnd() {
            if (pos != tokens.size()) {
                throw new ConditionEvaluationException("Unexpected trailing token: " + peek());
            }
        }

        private boolean parseOr() {
            boolean result = parseAnd();
            while (peekEquals("||")) {
                pos++;
                boolean rhs = parseAnd();
                result = result || rhs;
            }
            return result;
        }

        private boolean parseAnd() {
            boolean result = parseUnary();
            while (peekEquals("&&")) {
                pos++;
                boolean rhs = parseUnary();
                result = result && rhs;
            }
            return result;
        }

        private boolean parseUnary() {
            if (peekEquals("!")) {
                pos++;
                return !parseUnary();
            }
            if (peekEquals("(")) {
                pos++;
                boolean result = parseOr();
                expect(")");
                return result;
            }
            return parseComparison();
        }

        private boolean parseComparison() {
            String field = expectIdentifier().toUpperCase(Locale.ROOT);

            if (BOOLEAN_FIELDS.containsKey(field)) {
                // Boolean field: may stand alone, must NOT be followed by a comparator
                if (peekIsComparator()) {
                    throw new ConditionEvaluationException(
                            "Boolean field '" + field + "' cannot be used with a comparator");
                }
                return BOOLEAN_FIELDS.get(field).test(cal);
            }

            if (NUMERIC_FIELDS.containsKey(field)) {
                if (!peekIsComparator()) {
                    throw new ConditionEvaluationException(
                            "Numeric field '" + field + "' requires a comparator, e.g. " + field + "=1");
                }
                String comparator = consumeComparator();
                int actual = NUMERIC_FIELDS.get(field).applyAsInt(cal);

                if ("IN".equals(comparator) || "NOT IN".equals(comparator)) {
                    Set<Integer> values = parseValueList();
                    boolean contains = values.contains(actual);
                    return "IN".equals(comparator) == contains;
                } else {
                    int expected = parseNumber();
                    return applyComparator(comparator, actual, expected);
                }
            }

            throw new ConditionEvaluationException("Unknown condition field: " + field);
        }

        private boolean applyComparator(String comparator, int actual, int expected) {
            switch (comparator) {
                case "=":
                case "==":
                    return actual == expected;
                case "!=":
                    return actual != expected;
                case ">":
                    return actual > expected;
                case ">=":
                    return actual >= expected;
                case "<":
                    return actual < expected;
                case "<=":
                    return actual <= expected;
                default:
                    throw new ConditionEvaluationException("Unsupported comparator: " + comparator);
            }
        }

        private Set<Integer> parseValueList() {
            expect("(");
            Set<Integer> values = new HashSet<>();
            values.add(parseNumber());
            while (peekEquals(",")) {
                pos++;
                values.add(parseNumber());
            }
            expect(")");
            return values;
        }

        private boolean peekIsComparator() {
            String t = peek();
            return t != null && (t.equals("=") || t.equals("==") || t.equals("!=") || t.equals(">")
                    || t.equals(">=") || t.equals("<") || t.equals("<=") || t.equalsIgnoreCase("IN")
                    || t.equalsIgnoreCase("NOT"));
        }

        private String consumeComparator() {
            String t = peek();
            if (t == null) {
                throw new ConditionEvaluationException("Expected comparator but reached end of input");
            }
            if (t.equalsIgnoreCase("NOT")) {
                pos++;
                expectKeyword("IN");
                return "NOT IN";
            }
            if (t.equalsIgnoreCase("IN")) {
                pos++;
                return "IN";
            }
            pos++;
            return t;
        }

        private int parseNumber() {
            String t = peek();
            if (t == null || !t.matches("-?\\d+")) {
                throw new ConditionEvaluationException("Expected a number but found: " + t);
            }
            int value = Integer.parseInt(t);
            if (value < 0) {
                throw new ConditionEvaluationException(
                        "Negative numbers are not allowed: " + value + " (field values must be positive)");
            }
            pos++;
            return value;
        }

        private String expectIdentifier() {
            String t = peek();
            if (t == null || !t.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new ConditionEvaluationException("Expected a field name but found: " + t);
            }
            pos++;
            return t;
        }

        private void expectKeyword(String keyword) {
            String t = peek();
            if (t == null || !t.equalsIgnoreCase(keyword)) {
                throw new ConditionEvaluationException("Expected '" + keyword + "' but found: " + t);
            }
            pos++;
        }

        private void expect(String literal) {
            String t = peek();
            if (t == null || !t.equals(literal)) {
                throw new ConditionEvaluationException("Expected '" + literal + "' but found: " + t);
            }
            pos++;
        }

        private boolean peekEquals(String literal) {
            String t = peek();
            return t != null && t.equals(literal);
        }

        private String peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }
    }

    /**
     * Thrown when a condition expression is malformed, unbalanced, or references an
     * unknown field. Callers (e.g. {@link com.ts.core.engine.WorkflowInstance}) are
     * expected to fail closed on this exception rather than defaulting to "always run".
     */
    public static class ConditionEvaluationException extends RuntimeException {
        /**
         * Constructs a new ConditionEvaluationException instance.
         *
         * @param message  detail message describing the parse or evaluation failure
         */
        public ConditionEvaluationException(String message) {
            super(message);
        }
    }
}