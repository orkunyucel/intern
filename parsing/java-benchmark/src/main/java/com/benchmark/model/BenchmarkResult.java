package com.benchmark.model;

/**
 * Immutable record holding benchmark results for serialization/deserialization
 * operations.
 */
public record BenchmarkResult(
        double serializationTimeSeconds,
        double deserializationTimeSeconds,
        long totalBytes,
        int recordCount) {
    /**
     * Returns average bytes per record
     */
    public double avgBytesPerRecord() {
        return recordCount > 0 ? (double) totalBytes / recordCount : 0;
    }

    /**
     * Returns serialization throughput in records per second
     */
    public long serializationThroughput() {
        return serializationTimeSeconds > 0 ? (long) (recordCount / serializationTimeSeconds) : 0;
    }

    /**
     * Returns deserialization throughput in records per second
     */
    public long deserializationThroughput() {
        return deserializationTimeSeconds > 0 ? (long) (recordCount / deserializationTimeSeconds) : 0;
    }

    /**
     * Returns serialization latency in microseconds per record
     */
    public double serializationLatencyMicros() {
        return recordCount > 0 ? (serializationTimeSeconds / recordCount) * 1_000_000 : 0;
    }

    /**
     * Returns deserialization latency in microseconds per record
     */
    public double deserializationLatencyMicros() {
        return recordCount > 0 ? (deserializationTimeSeconds / recordCount) * 1_000_000 : 0;
    }

    /**
     * Returns serialization time in milliseconds
     */
    public double serializationTimeMs() {
        return serializationTimeSeconds * 1000;
    }

    /**
     * Returns deserialization time in milliseconds
     */
    public double deserializationTimeMs() {
        return deserializationTimeSeconds * 1000;
    }
}
