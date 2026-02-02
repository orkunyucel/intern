package com.benchmark.generator;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates test records with 250 fields matching benchmark configuration.
 *
 * Field Distribution (Total 250 fields):
 * - 84 int fields (actual int values, max 9 digits to fit in int range)
 * - 83 long fields (actual long values, max 18 digits to fit in long range)
 * - 83 string fields (alphanumeric, max 30 chars)
 */
public class DataGenerator {

    public static final int INT_FIELDS = 84;
    public static final int LONG_FIELDS = 83;
    public static final int STRING_FIELDS = 83;
    public static final int TOTAL_FIELDS = INT_FIELDS + LONG_FIELDS + STRING_FIELDS;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final int STRING_LENGTH = 30; // max 30 chars

    private final Random random;

    public DataGenerator() {
        this.random = new SecureRandom();
    }

    public DataGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a single record with 250 fields.
     * Keys:
     * - int_0 ... int_83 (Integer values)
     * - long_0 ... long_82 (Long values)
     * - str_0 ... str_82 (String values, max 30 chars)
     */
    public Map<String, Object> generateRecord() {
        Map<String, Object> record = new HashMap<>(TOTAL_FIELDS);

        // int fields (actual int values)
        for (int i = 0; i < INT_FIELDS; i++) {
            record.put("int_" + i, generateRandomInt());
        }

        // long fields (actual long values)
        for (int i = 0; i < LONG_FIELDS; i++) {
            record.put("long_" + i, generateRandomLong());
        }

        // string fields (alphanumeric, max 30 chars)
        for (int i = 0; i < STRING_FIELDS; i++) {
            record.put("str_" + i, generateRandomString(STRING_LENGTH));
        }

        return record;
    }

    /**
     * Generates a single record as a pipe-separated string.
     * Format: int_0|...|int_83|long_0|...|long_82|str_0|...|str_82
     */
    public String generatePipeRecord() {
        StringBuilder sb = new StringBuilder(4096); // Pre-allocate decent size

        // int fields
        for (int i = 0; i < INT_FIELDS; i++) {
            sb.append(generateRandomInt()).append('|');
        }

        // long fields
        for (int i = 0; i < LONG_FIELDS; i++) {
            sb.append(generateRandomLong()).append('|');
        }

        // string fields
        for (int i = 0; i < STRING_FIELDS; i++) {
            sb.append(generateRandomString(STRING_LENGTH));
            if (i < STRING_FIELDS - 1) {
                sb.append('|');
            }
        }

        return sb.toString();
    }

    /**
     * Generates a random alphanumeric string of fixed length.
     */
    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a random positive int value.
     * Range: 100_000_000 to Integer.MAX_VALUE (9-10 digits)
     */
    private int generateRandomInt() {
        // Generate a large positive int (9 digits minimum)
        return 100_000_000 + random.nextInt(Integer.MAX_VALUE - 100_000_000);
    }

    /**
     * Generates a random positive long value.
     * Range: large positive longs (up to 18 digits)
     */
    private long generateRandomLong() {
        // Generate a large positive long (15-18 digits)
        long base = 100_000_000_000_000L; // 15 digits
        return base + (Math.abs(random.nextLong()) % (Long.MAX_VALUE - base));
    }

    /**
     * Test generation speed and sample output
     */
    public static void main(String[] args) {
        DataGenerator generator = new DataGenerator();

        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            generator.generateRecord();
        }
        long end = System.nanoTime();

        double durationMs = (end - start) / 1_000_000.0;
        System.out.printf("Generated 1000 map records in %.4f ms%n", durationMs);

        start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            generator.generatePipeRecord();
        }
        end = System.nanoTime();

        durationMs = (end - start) / 1_000_000.0;
        System.out.printf("Generated 1000 pipe records in %.4f ms%n", durationMs);

        // Print sample record
        String sample = generator.generatePipeRecord();
        System.out.println(
                "Sample Pipe Record (Snippet): " + sample.substring(0, Math.min(100, sample.length())) + "...");
    }
}
