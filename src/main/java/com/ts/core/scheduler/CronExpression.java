/*
 * Description: Parser and evaluator for 6-field cron expressions (second minute hour day-of-month month day-of-week)
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.core.scheduler;

import java.text.ParseException;
import java.util.*;
/**
 * Parser and evaluator for standard 6-field cron expressions
 * ({@code second minute hour day-of-month month day-of-week}).
 *
 * <p>Supported field syntax: {@code *}, numeric values, ranges ({@code 1-5}),
 * lists ({@code 1,3,5}), step values ({@code *}/{@code 5}), and {@code ?}
 * (wildcard for day-of-month or day-of-week only).
 *
 * <p>The day-of-month field additionally accepts the {@code L} family of
 * tokens, matching Quartz semantics: {@code L} is the last day of the month,
 * and {@code L-N} is the day {@code N} days before the last day, i.e. the
 * calendar day {@code lastDayOfMonth - N}. The offset is therefore resolved
 * against the month being tested, not at parse time, so {@code L} maps to
 * 31 in January, 28 or 29 in February, and {@code L-4} (the fifth-to-last
 * day) maps to 27 in January but 24 in February. When an offset reaches past
 * the first day of a short month the day simply does not match that month:
 * {@code L-29} fires only in months long enough to contain it. {@code L} may
 * be combined with other values in a list ({@code L-4,15} matches the
 * fifth-to-last day and the 15th), but not with ranges or steps.
 *
 * <p>Day-of-month and day-of-week fields follow strict Quartz semantics:
 * exactly one of the two fields must be {@code ?}. The field that is not
 * {@code ?} is the one used to determine the match; {@code ?} means "ignore
 * this field, defer entirely to the other one". Supplying both fields as
 * {@code ?}, or neither as {@code ?}, is rejected at parse time.
 *
 * <p>Usage: call {@link #getNextValidTimeAfter(java.util.Date)} to obtain
 * the next scheduled fire time. Returns {@code null} if no valid time is found
 * within the iteration limit (approximately 4 years).
 */
public class CronExpression {
    private final String cronExpression;
    private final TreeSet<Integer> seconds = new TreeSet<>();
    private final TreeSet<Integer> minutes = new TreeSet<>();
    private final TreeSet<Integer> hours = new TreeSet<>();
    private final TreeSet<Integer> daysOfMonth = new TreeSet<>();
    private final TreeSet<Integer> months = new TreeSet<>();
    private final TreeSet<Integer> daysOfWeek = new TreeSet<>();

    // Offsets collected from 'L' tokens in the day-of-month field. Each entry N
    // means "the day N days before the last day of the month" ('L' is stored as
    // 0, 'L-4' as 4). These cannot live in daysOfMonth: the calendar day a given
    // offset resolves to depends on the month (and on leap years), so it is only
    // known while matching, not while parsing.
    private final TreeSet<Integer> lastDayOfMonthOffsets = new TreeSet<>();

    // Literal '?' markers for the day-of-month / day-of-week fields, captured
    // from the raw field text at parse time. These must NOT be inferred from
    // set size later on, since '*' and '?' both expand to the full range and
    // are otherwise indistinguishable once parsed into a TreeSet.
    private boolean dayOfMonthIsQuestionMark;
    private boolean dayOfWeekIsQuestionMark;

    /**
     * Constructor with cron expression
     *
     * @param cronExpression Cron expression
     * @throws ParseException if the expression is invalid
     */
    public CronExpression(String cronExpression) throws ParseException {
        this.cronExpression = cronExpression;
        parse();
    }

    /**
     * Validate a cron expression
     *
     * @param cronExpression Cron expression to validate
     * @throws ParseException if the expression is invalid
     */
    public static void validateExpression(String cronExpression) throws ParseException {
        new CronExpression(cronExpression);
    }

    /**
     * Parse the cron expression
     *
     * @throws ParseException if the expression is invalid
     */
    private void parse() throws ParseException {
        String[] fields = cronExpression.split("\\s+");

        if (fields.length != 6) {
            throw new ParseException("Cron expression must have 6 fields", 0);
        }

        parseField(seconds, fields[0], 0, 59, false);       // Seconds
        parseField(minutes, fields[1], 0, 59, false);       // Minutes
        parseField(hours, fields[2], 0, 23, false);         // Hours
        parseField(daysOfMonth, fields[3], 1, 31, true);    // Days of month ('L' allowed)
        parseField(months, fields[4], 1, 12, false);        // Months
        parseField(daysOfWeek, fields[5], 0, 7, false);     // Days of week (0 or 7 is Sunday)

        // Capture the literal '?' markers from the raw field text. This must be
        // done from the source string, not from the resulting TreeSet, because
        // '*' and '?' both expand to fill the entire range and are otherwise
        // indistinguishable after parseField() runs.
        dayOfMonthIsQuestionMark = fields[3].equals("?");
        dayOfWeekIsQuestionMark = fields[5].equals("?");

        // Enforce Quartz-compatible semantics: exactly one of day-of-month /
        // day-of-week must be '?'. Allowing both (or neither) to be '?' makes
        // the OR-vs-ignore semantics ambiguous, so reject it up front.
        if (dayOfMonthIsQuestionMark == dayOfWeekIsQuestionMark) {
            throw new ParseException(
                    "Exactly one of day-of-month and day-of-week must be '?': " + cronExpression, 0);
        }

        // Normalise day-of-week so that '0' and '7' (both mean Sunday) collapse
        // to a single representative value. Without this, an expression such as
        // "? * 0" and "? * 7" would parse into different internal sets even
        // though they mean the same thing, and any later matching logic keyed
        // off the presence of '0' would misbehave.
        if (daysOfWeek.remove(0)) {
            daysOfWeek.add(7);
        }
    }

    /**
     * Parse a field of the cron expression
     *
     * @param target        Target set to store values
     * @param field         Field string
     * @param min           Minimum allowed value
     * @param max           Maximum allowed value
     * @param allowLastDay  Whether the 'L' / 'L-N' tokens are accepted in this
     *                      field. Only the day-of-month field allows them; any
     *                      other field must reject them rather than fall through
     *                      to the numeric branches and produce a confusing error.
     * @throws ParseException if the field is invalid
     */
    private void parseField(TreeSet<Integer> target, String field, int min, int max,
                            boolean allowLastDay) throws ParseException {
        // Handle the '?' wildcard (only valid for day-of-month and day-of-week fields)
        if (field.equals("?")) {
            // For day-of-month and day-of-week fields, '?' means 'unspecified'
            // In practice this means accepting any value for that field
            for (int i = min; i <= max; i++) {
                target.add(i);
            }
            return;
        }

        if (field.equals("*")) {
            // All values in range
            for (int i = min; i <= max; i++) {
                target.add(i);
            }
            return;
        }

        // Handle comma-separated values
        String[] parts = field.split(",");

        for (String part : parts) {
            // Handle 'L' / 'L-N'. This must be tested BEFORE the range branch
            // below: 'L-4' contains '-' and would otherwise be split into
            // ["L", "4"] and fail on Integer.parseInt("L").
            if (part.startsWith("L")) {
                if (!allowLastDay) {
                    throw new ParseException(
                            "'L' is only valid in the day-of-month field: " + part, 0);
                }
                lastDayOfMonthOffsets.add(parseLastDayOffset(part));
                continue;
            }

            // Handle range (e.g., "1-5")
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length != 2) {
                    throw new ParseException("Invalid range: " + part, 0);
                }

                int start, end;
                try {
                    start = Integer.parseInt(range[0]);
                    end = Integer.parseInt(range[1]);
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid range values: " + part, 0);
                }

                if (start < min || end > max || start > end) {
                    throw new ParseException("Invalid range: " + part, 0);
                }

                for (int i = start; i <= end; i++) {
                    target.add(i);
                }
            }
            // Handle step (e.g., "*/5" or "1/5")
            else if (part.contains("/")) {
                String[] steps = part.split("/");
                if (steps.length != 2) {
                    throw new ParseException("Invalid step: " + part, 0);
                }

                int step;
                try {
                    step = Integer.parseInt(steps[1]);
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid step value: " + part, 0);
                }

                if (step <= 0) {
                    throw new ParseException("Step must be positive: " + part, 0);
                }

                if (steps[0].equals("*")) {
                    // Every 'step' values in the range
                    for (int i = min; i <= max; i += step) {
                        target.add(i);
                    }
                } else {
                    int start;
                    try {
                        start = Integer.parseInt(steps[0]);
                    } catch (NumberFormatException e) {
                        throw new ParseException("Invalid start value for step: " + part, 0);
                    }

                    if (start < min || start > max) {
                        throw new ParseException("Start value out of range: " + part, 0);
                    }

                    for (int i = start; i <= max; i += step) {
                        target.add(i);
                    }
                }
            }
            // Handle single value
            else {
                int value;
                try {
                    value = Integer.parseInt(part);
                } catch (NumberFormatException e) {
                    throw new ParseException("Invalid value: " + part, 0);
                }

                if (value < min || value > max) {
                    throw new ParseException("Value out of range: " + part, 0);
                }

                target.add(value);
            }
        }
    }

    /**
     * Parse an 'L' token from the day-of-month field into a last-day offset.
     *
     * <p>Accepts {@code L} (offset 0) and {@code L-N} (offset N). Ranges and
     * steps are rejected: {@code L/2} and {@code L-4-5} have no sensible
     * meaning, and Quartz rejects them too.
     *
     * @param part Token to parse, known to start with 'L'
     * @return Offset in days before the last day of the month (0 for 'L')
     * @throws ParseException if the token is malformed or the offset is unreachable
     */
    private int parseLastDayOffset(String part) throws ParseException {
        if (part.equals("L")) {
            return 0;
        }

        if (!part.startsWith("L-")) {
            throw new ParseException("Invalid 'L' token: " + part, 0);
        }

        int offset;
        try {
            offset = Integer.parseInt(part.substring(2));
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid 'L' offset: " + part, 0);
        }

        // 30 is the largest offset that can still land on a real day (day 1 of a
        // 31-day month). Anything beyond that can never match in any month, so
        // reject it at parse time instead of scheduling a job that never fires.
        if (offset < 0 || offset > 30) {
            throw new ParseException("'L' offset out of range (0-30): " + part, 0);
        }

        return offset;
    }

    /**
     * Get the next valid time after the specified date
     * Optimized algorithm to reduce iteration
     *
     * @param after Date to find the next valid time after
     * @return Next valid time
     */
    public Date getNextValidTimeAfter(Date after) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(after);
        calendar.set(Calendar.MILLISECOND, 0);

        // Try to find the next valid time with efficient algorithm
        return findNextValidTime(calendar);
    }

    /**
     * Find the next valid time with an optimized algorithm
     *
     * @param calendar Starting calendar
     * @return Next valid date or null if none found
     */
    private Date findNextValidTime(Calendar calendar) {
        // Cap the number of iterations to prevent infinite loops
        final int MAX_ITERATIONS = 1_000_000;
        int iterations = 0;

        // Ensure we compute the next future time, not the current instant
        calendar.add(Calendar.SECOND, 1);

        while (iterations++ < MAX_ITERATIONS) {
            // Retrieve the current value of each calendar field
            int second = calendar.get(Calendar.SECOND);
            int minute = calendar.get(Calendar.MINUTE);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            int month = calendar.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based; add 1 to get 1-12
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1; // Calendar.DAY_OF_WEEK is 1-based (1=Sunday); subtract 1 for 0-based

            // Normalise day-of-week so that Sunday is represented as 7, matching
            // the normalisation applied to the parsed daysOfWeek set in parse().
            if (dayOfWeek == 0) {
                dayOfWeek = 7;
            }

            // Process seconds field
            Integer nextSecond = seconds.ceiling(second);
            if (nextSecond == null) {
                nextSecond = seconds.first();
                calendar.set(Calendar.SECOND, nextSecond);
                calendar.add(Calendar.MINUTE, 1);
                continue;
            } else if (nextSecond > second) {
                calendar.set(Calendar.SECOND, nextSecond);
                continue;
            }

            // Process minutes field
            if (!minutes.contains(minute)) {
                Integer nextMinute = minutes.ceiling(minute);
                if (nextMinute == null) {
                    nextMinute = minutes.first();
                    calendar.set(Calendar.MINUTE, nextMinute);
                    calendar.set(Calendar.SECOND, seconds.first());
                    calendar.add(Calendar.HOUR_OF_DAY, 1);
                    continue;
                } else {
                    calendar.set(Calendar.MINUTE, nextMinute);
                    calendar.set(Calendar.SECOND, seconds.first());
                    continue;
                }
            }

            // Process hours field
            if (!hours.contains(hour)) {
                Integer nextHour = hours.ceiling(hour);
                if (nextHour == null) {
                    nextHour = hours.first();
                    calendar.set(Calendar.HOUR_OF_DAY, nextHour);
                    calendar.set(Calendar.MINUTE, minutes.first());
                    calendar.set(Calendar.SECOND, seconds.first());
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    continue;
                } else {
                    calendar.set(Calendar.HOUR_OF_DAY, nextHour);
                    calendar.set(Calendar.MINUTE, minutes.first());
                    calendar.set(Calendar.SECOND, seconds.first());
                    continue;
                }
            }

            // Process month field
            if (!months.contains(month)) {
                Integer nextMonth = months.ceiling(month);
                if (nextMonth == null) {
                    nextMonth = months.first();
                    calendar.set(Calendar.MONTH, nextMonth - 1);
                    calendar.set(Calendar.DAY_OF_MONTH, 1);
                    calendar.add(Calendar.YEAR, 1);
                } else {
                    calendar.set(Calendar.MONTH, nextMonth - 1);
                    calendar.set(Calendar.DAY_OF_MONTH, 1);
                }
                calendar.set(Calendar.HOUR_OF_DAY, hours.first());
                calendar.set(Calendar.MINUTE, minutes.first());
                calendar.set(Calendar.SECOND, seconds.first());
                continue;
            }

            // Day-of-month / day-of-week matching. Quartz semantics: exactly one
            // of the two fields is '?' (enforced at parse time), and the field
            // that is NOT '?' is the one that determines the match. We branch on
            // the literal '?' markers captured during parsing rather than on set
            // size, since '*' and '?' expand to identical sets and would
            // otherwise be indistinguishable here.
            boolean dayOfMonthMatches = matchesDayOfMonth(calendar, dayOfMonth);
            boolean dayOfWeekMatches = daysOfWeek.contains(dayOfWeek);

            if (dayOfMonthIsQuestionMark) {
                // Day-of-month is '?': only day-of-week determines the match
                if (!dayOfWeekMatches) {
                    calendar.add(Calendar.DAY_OF_WEEK, 1);
                    resetTimeFields(calendar);
                    continue;
                }
            } else {
                // Day-of-week is '?': only day-of-month determines the match
                if (!dayOfMonthMatches) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    resetTimeFields(calendar);
                    continue;
                }
            }

            // All fields match; return this candidate time
            return calendar.getTime();
        }

        // Reached iteration limit; return null
        return null;
    }

    /**
     * Test whether the given day-of-month value matches, taking any 'L' / 'L-N'
     * tokens into account.
     *
     * <p>The offset is resolved against the month the calendar currently sits
     * in, so {@code L} is 31 in January but 28 or 29 in February. An offset that
     * reaches past the first day of a short month yields no match for that
     * month; {@code L-29} fires only in months long enough to contain it, which
     * is the intended behaviour rather than an error.
     *
     * @param calendar Calendar positioned within the month being tested
     * @param dayOfMonth Day-of-month value to test (1-31)
     * @return true if the day matches the explicit set or one of the offsets
     */
    private boolean matchesDayOfMonth(Calendar calendar, int dayOfMonth) {
        if (daysOfMonth.contains(dayOfMonth)) {
            return true;
        }

        if (lastDayOfMonthOffsets.isEmpty()) {
            return false;
        }

        int lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int offset : lastDayOfMonthOffsets) {
            if (dayOfMonth == lastDayOfMonth - offset) {
                return true;
            }
        }

        return false;
    }

    private void resetTimeFields(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, hours.first());   // Set to first allowed hour
        calendar.set(Calendar.MINUTE, minutes.first());      // Set to first allowed minute
        calendar.set(Calendar.SECOND, seconds.first());      // Set to first allowed second
        calendar.set(Calendar.MILLISECOND, 0);               // Always clear milliseconds
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return cronExpression;
    }
}
