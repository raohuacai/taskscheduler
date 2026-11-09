/*
 * Description: Thread-safe date formatting and time-difference utilities
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
/**
 * Utility class providing date formatting and time-difference calculation helpers.
 *
 * <p>All methods are stateless and thread-safe (they instantiate a new
 * {@link java.text.SimpleDateFormat} per call rather than sharing one).
 */
public class DateUtils {
    private static final Logger logger = LogManager.getLogger(DateUtils.class);

    /**
     * Formats a Date object into a string using the given pattern
     *
     * @param date    the date to format
     * @param pattern the date/time format pattern (e.g. "yyyy-MM-dd HH:mm:ss")
     * @return formatted date string, or empty string if date is null
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return "";
        }

        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        return formatter.format(date);
    }

    /**
     * Calculates the elapsed time between two dates in seconds
     *
     * @param startDate the start date
     * @param endDate   the end date
     * @return difference in seconds; returns 0 if either date is null
     */
    public static long getTimeDifferenceInSeconds(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }

        return (endDate.getTime() - startDate.getTime()) / 1000;
    }

    /**
     * Returns the current date time string.
     *
     * @return the result as a String
     */
    public static String getCurrentDateTimeString() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        return formatter.format(new Date());
    }
}
