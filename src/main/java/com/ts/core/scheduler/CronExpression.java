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
 * <p>Day-of-month and day-of-week fields are evaluated with OR semantics when
 * both are non-wildcard, consistent with Quartz Scheduler behaviour.
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

        parseField(seconds, fields[0], 0, 59);       // Seconds
        parseField(minutes, fields[1], 0, 59);       // Minutes
        parseField(hours, fields[2], 0, 23);         // Hours
        parseField(daysOfMonth, fields[3], 1, 31);   // Days of month
        parseField(months, fields[4], 1, 12);        // Months
        parseField(daysOfWeek, fields[5], 0, 7);     // Days of week (0 or 7 is Sunday)
    }

    /**
     * Parse a field of the cron expression
     *
     * @param target Target set to store values
     * @param field  Field string
     * @param min    Minimum allowed value
     * @param max    Maximum allowed value
     * @throws ParseException if the field is invalid
     */
    private void parseField(TreeSet<Integer> target, String field, int min, int max) throws ParseException {
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

            // Normalise day-of-week so that both 0 and 7 represent Sunday
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

            // Use OR logic: the expression matches when either day-of-month OR day-of-week matches
            boolean dayOfMonthMatches = daysOfMonth.contains(dayOfMonth);
            boolean dayOfWeekMatches = daysOfWeek.contains(0) || daysOfWeek.contains(dayOfWeek);

            // new logic here, start
            boolean dayOfMonthQuestionMark = daysOfMonth.size() == 31; // "?" in day-of-month
            boolean dayOfWeekQuestionMark = daysOfWeek.size() == 8;    // "?" in day-of-week
            // Apply correct logic:
            if (dayOfMonthQuestionMark) {
                // If day-of-month is "?", only check day-of-week
                if (!dayOfWeekMatches) {
                    calendar.add(Calendar.DAY_OF_WEEK, 1);
                    resetTimeFields(calendar);
                    continue;
                }
            } else if (dayOfWeekQuestionMark) {
                // If day-of-week is "?", only check day-of-month
                if (!dayOfMonthMatches) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    resetTimeFields(calendar);
                    continue;
                }
            } else {
                // If neither is "?", use OR logic
                if (!(dayOfMonthMatches || dayOfWeekMatches)) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    resetTimeFields(calendar);
                    continue;
                }
            }
            // new logic here, end

            // Special handling for day-of-month and day-of-week fields
            if (daysOfMonth.size() == 31 && daysOfWeek.size() == 8) {
                // When both fields are wildcards (*), the time always matches
                dayOfMonthMatches = true;
                dayOfWeekMatches = true;
            }

            // Apply OR logic between day-of-month and day-of-week
            if (!(dayOfMonthMatches || dayOfWeekMatches)) {
                // Advance to the next day
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, hours.first());
                calendar.set(Calendar.MINUTE, minutes.first());
                calendar.set(Calendar.SECOND, seconds.first());
                continue;
            }

            // All fields match; return this candidate time
            return calendar.getTime();
        }

        // Reached iteration limit; return null
        return null;
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
