/*
 * Description: Thread-safe timestamp-prefixed sequential ID generator with per-second sequence reset
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
/**
 * Thread-safe unique ID generator producing timestamp-prefixed sequential IDs.
 *
 * <p>Format: {@code yyMMddHHmmss} + zero-padded sequence (4 digits initially;
 * promoted to 9 digits if the sequence exceeds 9999 within the same second).
 * The sequence resets to 1 on each new second. A {@link java.util.concurrent.locks.ReadWriteLock}
 * per ID type prevents lost updates and ensures monotonicity across threads.
 */
public class UniqueIdGenerator {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final int INITIAL_SEQUENCE_LENGTH = 4;
    private static final int EXTENDED_SEQUENCE_LENGTH = 9;
    private static final long SEQUENCE_LIMIT_DIGITS = 9999L; // Upper limit for the 4-digit sequence (value: 9999)

    /**
     * Enumeration of logical ID categories managed by this generator.
     * Each type maintains an independent sequence counter and date tracker,
     * so IDs from different types never collide even within the same second.
     */
    public enum IdType {
        TIME_WHEEL,
        WF_PARAM,
        TASK_PARAM,
        TASK_LOG,
        EVENT
    }

    // Stores per-type sequence state (counter + current date) in a thread-safe ConcurrentHashMap
    private static final Map<IdType, SequenceData> sequenceMap = new ConcurrentHashMap<>();

    /**
     * Inner class holding sequence state for a single ID type
     */
    private static class SequenceData {
        private String currentDate;
        private AtomicLong sequence;
        private int sequenceLength;
        private final ReadWriteLock rwLock;

        /**
         * Constructs a new SequenceData for the given date string.
         * The sequence counter starts at 0 and the sequence length begins at
         * {@link UniqueIdGenerator#INITIAL_SEQUENCE_LENGTH} (4 digits).
         *
         * @param initialDate the date string ({@code yyMMddHHmmss} format) used as the prefix
         */
        public SequenceData(String initialDate) {
            this.currentDate = initialDate;
            this.sequence = new AtomicLong(0);
            this.sequenceLength = INITIAL_SEQUENCE_LENGTH;
            this.rwLock = new ReentrantReadWriteLock();
        }
    }

    /**
     * Generates a unique ID for the specified ID type
     *
     * @param type the ID type to generate an ID for
     * @return a unique ID string
     */
    public static String generateId(IdType type) {
        LocalDateTime now = LocalDateTime.now();
        String today = now.format(DATE_FORMATTER);

        // Retrieve or initialize the sequence state for this ID type
        SequenceData data = sequenceMap.computeIfAbsent(type, k -> new SequenceData(today));

        // Acquire read lock
        data.rwLock.readLock().lock();
        try {
            // Check whether the date has rolled over, or whether the sequence needs to be promoted to more digits
            if (!today.equals(data.currentDate) ||
                    (data.sequenceLength == INITIAL_SEQUENCE_LENGTH && data.sequence.get() >= SEQUENCE_LIMIT_DIGITS)) {

                // Release read lock，Acquire write lock
                data.rwLock.readLock().unlock();
                data.rwLock.writeLock().lock();

                try {
                    // Double-checked locking pattern
                    if (!today.equals(data.currentDate)) {
                        // Date has changed; reset the sequence counter
                        data.currentDate = today;
                        data.sequence.set(0);
                        // If the sequence was already promoted to extended digits, keep the extended format
                    } else if (data.sequenceLength == INITIAL_SEQUENCE_LENGTH && data.sequence.get() >= SEQUENCE_LIMIT_DIGITS) {
                        // Sequence has exceeded the 4-digit limit; upgrade to 9-digit format
                        data.sequenceLength = EXTENDED_SEQUENCE_LENGTH;
                    }

                    // Downgrade from write lock to read lock
                    data.rwLock.readLock().lock();
                } finally {
                    data.rwLock.writeLock().unlock();
                }
            }

            // Retrieve and atomically increment the sequence counter
            long nextSequence = data.sequence.incrementAndGet();

            // Format the sequence number to the required digit width
            String sequenceStr;
            if (data.sequenceLength == INITIAL_SEQUENCE_LENGTH) {
                sequenceStr = String.format("%04d", nextSequence);
            } else {
                sequenceStr = String.format("%09d", nextSequence);
            }

            // Assemble final ID: date/time prefix + sequence number
            return data.currentDate + sequenceStr;
        } finally {
            data.rwLock.readLock().unlock();
        }
    }
}