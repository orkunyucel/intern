package com.benchmark.runner.split;

import com.benchmark.model.BenchmarkResult;
import com.benchmark.model.FieldSpec;
import com.benchmark.model.FieldType;
import com.benchmark.runner.BenchmarkStrategy;

import java.io.IOException;
import java.util.List;

import static com.benchmark.generator.DataGenerator.*;

/**
 * Baseline benchmark: String.split() parsing.
 * Measures the cost of parsing pipe-separated strings without any binary
 * format.
 */
public class SplitBenchmark implements BenchmarkStrategy {

    private final List<FieldSpec> selectedFields;
    private volatile long outputSink;

    public SplitBenchmark(List<FieldSpec> selectedFields) {
        this.selectedFields = selectedFields;
    }

    @Override
    public String getName() {
        return "Split";
    }

    @Override
    public BenchmarkResult measureSelected(List<String> records) throws IOException {
        long start = System.nanoTime();
        long outputChars = 0;

        for (String record : records) {
            String out = parseSelectedFields(record);
            outputChars += out.length();
        }

        long end = System.nanoTime();
        outputSink = outputChars;
        double timeSeconds = (end - start) / 1_000_000_000.0;

        // We use 'serializationTime' fields to store 'Parsing Time' for this unified
        // view
        return new BenchmarkResult(timeSeconds, 0, 0, records.size());
    }

    @Override
    public BenchmarkResult measureFull(List<String> records) throws IOException {
        long start = System.nanoTime();

        for (String record : records) {
            parseRecord(record);
        }

        long end = System.nanoTime();
        double timeSeconds = (end - start) / 1_000_000_000.0;

        return new BenchmarkResult(timeSeconds, 0, 0, records.size());
    }

    /**
     * Parses a pipe-separated string into object types.
     * Throws exception if format is invalid to ensure strict parsing.
     */
    private void parseRecord(String record) {
        // Limit -1 to include empty trailing strings if any
        String[] parts = record.split("\\|", -1);

        // Validation (optional, but realistic)
        if (parts.length < TOTAL_FIELDS) {
            // In a real CDR processor, this would be an error log
            return;
        }

        int index = 0;

        // Parse ints
        for (int i = 0; i < INT_FIELDS; i++) {
            int val = Integer.parseInt(parts[index++]);
        }

        // Parse longs
        for (int i = 0; i < LONG_FIELDS; i++) {
            long val = Long.parseLong(parts[index++]);
        }

        // Parse strings
        // No parsing needed for strings, just accessing
        for (int i = 0; i < STRING_FIELDS; i++) {
            String val = parts[index++];
        }
    }

    /**
     * Parses only selected fields after full split, simulating array access.
     */
    private String parseSelectedFields(String record) {
        String[] parts = record.split("\\|", -1);
        StringBuilder sb = new StringBuilder(256);
        boolean first = true;

        for (FieldSpec field : selectedFields) {
            String value = parts[field.position()];
            if (field.type() == FieldType.INT) {
                int val = Integer.parseInt(value);
                if (!first) {
                    sb.append('|');
                }
                sb.append(val);
            } else if (field.type() == FieldType.LONG) {
                long val = Long.parseLong(value);
                if (!first) {
                    sb.append('|');
                }
                sb.append(val);
            } else {
                if (!first) {
                    sb.append('|');
                }
                sb.append(value);
            }
            first = false;
        }
        return sb.toString();
    }
}
