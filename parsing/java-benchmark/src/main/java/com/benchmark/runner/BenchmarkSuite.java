package com.benchmark.runner;

import com.benchmark.generator.DataGenerator;
import com.benchmark.model.BenchmarkResult;
import com.benchmark.model.FieldSpec;
import com.benchmark.model.FieldType;
import com.benchmark.runner.avro.AvroBenchmark;
import com.benchmark.runner.proto.ProtobufBenchmark;
import com.benchmark.runner.split.SplitBenchmark;
import org.apache.avro.Schema;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main benchmark suite that runs Avro vs Protobuf vs Split comparison tests.
 *
 * Methodology:
 * - Dataset Sizes: 5k, 20k, 50k records
 * - Repetitions: 5 (dropping min/max, averaging middle 3)
 * - Fairness: Alternating execution order
 * - Metrics: Breakdown of Serialization (Ingestion) vs Deserialization
 * - Data Source: Pipe-separated strings (simulating raw ingestion)
 */
public class BenchmarkSuite {

    private static final int[] DATASET_SIZES = { 1000000 };
    private static final int WARMUP_SIZE = 2000;
    private static final int REPETITIONS = 1;
    private static final int SELECTED_FIELD_COUNT = 10;
    private static final long FIELD_SELECTION_SEED = 42L;

    private final PrintWriter logWriter;
    private final List<BenchmarkStrategy> strategies;
    private final DataGenerator dataGenerator;
    private final List<FieldSpec> selectedFields;

    public BenchmarkSuite(String logFile, List<BenchmarkStrategy> strategies, List<FieldSpec> selectedFields)
            throws IOException {
        this.logWriter = new PrintWriter(new FileWriter(logFile));
        this.strategies = strategies;
        this.dataGenerator = new DataGenerator();
        this.selectedFields = selectedFields;
    }

    private void log(String message) {
        System.out.println(message);
        logWriter.println(message);
        logWriter.flush();
    }

    private void logf(String format, Object... args) {
        log(String.format(format, args));
    }

    /**
     * Generates test records as pipe-separated strings
     */
    private List<String> generateRecords(int count) {
        List<String> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            records.add(dataGenerator.generatePipeRecord());
        }
        return records;
    }

    /**
     * Runs a single benchmark step
     */
    private StepResults runStep(int size, int repetitionIndex) throws IOException {
        List<String> records = generateRecords(size);

        // Force GC before measurement
        System.gc();

        // Alternating execution order
        int offset = repetitionIndex % strategies.size();
        Map<String, BenchmarkResult> results = new HashMap<>();
        Map<String, ResultWithCpu> cpuResults = new HashMap<>();

        for (int i = 0; i < strategies.size(); i++) {
            int currentIndex = (offset + i) % strategies.size();
            BenchmarkStrategy strategy = strategies.get(currentIndex);

            ResultWithCpu selected = measureWithCpu(() -> strategy.measureSelected(records));
            ResultWithCpu full = measureWithCpu(() -> strategy.measureFull(records));

            results.put(strategy.getName() + "_selected", selected.result);
            results.put(strategy.getName() + "_full", full.result);

            cpuResults.put(strategy.getName() + "_selected", selected);
            cpuResults.put(strategy.getName() + "_full", full);

            System.gc();
        }
        return new StepResults(results, cpuResults);
    }

    /**
     * Runs the complete benchmark suite
     */
    public void run() throws IOException {
        log("Initializing Benchmark Suite (1 Million Records)...");
        log("");

        log("Environment:");
        logf("- Java: %s", System.getProperty("java.version"));
        logf("- OS: %s %s", System.getProperty("os.name"), System.getProperty("os.version"));
        logf("- Processors: %d", Runtime.getRuntime().availableProcessors());
        logf("- Max Memory: %d MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        log("");
        log("Scenario Definitions:");
        log("X (10 fields): parse only 10 fields and re-emit as pipe-delimited string.");
        log("Y (250 fields): parse all 250 fields (full parsing).");
        log("");
        log("Selected fields for X (fixed per run):");
        log(selectedFields.stream()
                .map(f -> String.format("%s(pos=%d,field=%d)", f.name(), f.position(), f.fieldNumber()))
                .collect(Collectors.joining(", ")));
        log("");
        log("Note: Each strategy measures full end-to-end path per run (pipe -> encode -> decode -> output).");
        log("");

        Map<String, BenchmarkResult> lastResults = new HashMap<>();
        Map<String, CpuStats> lastCpuResults = new HashMap<>();

        for (int size : DATASET_SIZES) {
            logf(">> Generating Data: %d records...", size);

            // Warmup
            System.out.print("  Warm-up...");
            runStep(WARMUP_SIZE, 0);
            System.out.println(" Done.");

            // Repetitions
            List<Map<String, BenchmarkResult>> allResults = new ArrayList<>();
            List<Map<String, ResultWithCpu>> allCpuResults = new ArrayList<>();
            System.out.print("  Running " + REPETITIONS + " repetitions...");
            for (int i = 0; i < REPETITIONS; i++) {
                System.out.print(".");
                StepResults stepResults = runStep(size, i);
                allResults.add(stepResults.results());
                allCpuResults.add(stepResults.cpuResults());
                logStepResults(i + 1, stepResults.results(), stepResults.cpuResults());
            }
            System.out.println(" Done.");

            for (BenchmarkStrategy strategy : strategies) {
                String name = strategy.getName();
                BenchmarkResult avgSelected = calculateAverage(allResults, name + "_selected", size);
                BenchmarkResult avgFull = calculateAverage(allResults, name + "_full", size);

                lastResults.put(name + "_selected", avgSelected);
                lastResults.put(name + "_full", avgFull);
            }

            lastCpuResults = new HashMap<>();
            for (BenchmarkStrategy strategy : strategies) {
                String name = strategy.getName();
                CpuStats avgSelectedCpu = calculateCpuAverage(allCpuResults, name + "_selected");
                CpuStats avgFullCpu = calculateCpuAverage(allCpuResults, name + "_full");

                lastCpuResults.put(name + "_selected", avgSelectedCpu);
                lastCpuResults.put(name + "_full", avgFullCpu);
            }
        }

        printFinalLog(lastResults, lastCpuResults);

        logWriter.close();
    }

    private BenchmarkResult calculateAverage(List<Map<String, BenchmarkResult>> results, String key, int size) {
        List<BenchmarkResult> keyResults = results.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(BenchmarkResult::serializationTimeSeconds))
                .collect(Collectors.toList());

        if (keyResults.size() >= 3) {
            keyResults = keyResults.subList(1, keyResults.size() - 1);
        }

        double avgTime = keyResults.stream().mapToDouble(BenchmarkResult::serializationTimeSeconds).average().orElse(0);

        // We only care about parsing time (stored in serializationTime fields)
        return new BenchmarkResult(avgTime, 0, 0, size);
    }

    private void printFinalLog(Map<String, BenchmarkResult> results, Map<String, CpuStats> cpuStats) {
        log("");
        log("FINAL RESULTS");
        log("");
        log("X (10 fields) - parse 10 fields and re-emit as pipe:");
        log(String.format("%-10s %-12s %-12s %-15s %-14s %-16s", "Yöntem", "Süre(ms)", "Süre(s)", "Hız(rec/s)",
                "CPU(s)", "CPU(%)"));
        log("--------------------------------------------------------------------------------------");
        for (BenchmarkStrategy strategy : strategies) {
            String name = strategy.getName();
            BenchmarkResult selected = results.get(name + "_selected");
            CpuStats selectedCpu = cpuStats.get(name + "_selected");
            log(String.format("%-10s %10.0f %12.2f %15,d %14s %16s",
                    name,
                    selected.serializationTimeMs(),
                    selected.serializationTimeSeconds(),
                    selected.serializationThroughput(),
                    formatCpuTime(selectedCpu),
                    formatCpuUsage(selectedCpu)));
        }

        log("");
        log("Y (250 fields) - full parse of all fields:");
        log(String.format("%-10s %-12s %-12s %-15s %-14s %-16s", "Yöntem", "Süre(ms)", "Süre(s)", "Hız(rec/s)",
                "CPU(s)", "CPU(%)"));
        log("--------------------------------------------------------------------------------------");
        for (BenchmarkStrategy strategy : strategies) {
            String name = strategy.getName();
            BenchmarkResult full = results.get(name + "_full");
            CpuStats fullCpu = cpuStats.get(name + "_full");
            log(String.format("%-10s %10.0f %12.2f %15,d %14s %16s",
                    name,
                    full.serializationTimeMs(),
                    full.serializationTimeSeconds(),
                    full.serializationThroughput(),
                    formatCpuTime(fullCpu),
                    formatCpuUsage(fullCpu)));
        }

        log("");
        log("Not: GPU Usage verisi Java Runtime üzerinden direkt alınamadığı için");
        log("CPU Processing Time ve Memory Usage dolaylı gösterge olarak kabul edilebilir.");
    }

    private String getCpuSummary() {
        java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
        if (baseBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            double processLoad = osBean.getProcessCpuLoad();
            double systemLoad = osBean.getSystemCpuLoad();
            long cpuTimeNanos = osBean.getProcessCpuTime();
            double cpuTimeSeconds = cpuTimeNanos / 1_000_000_000.0;

            String processLoadStr = processLoad >= 0 ? String.format("%.1f%%", processLoad * 100) : "N/A";
            String systemLoadStr = systemLoad >= 0 ? String.format("%.1f%%", systemLoad * 100) : "N/A";
            String cpuTimeStr = cpuTimeNanos >= 0 ? String.format("%.2f s", cpuTimeSeconds) : "N/A";

            return String.format("Process CPU Time: %s | Process CPU Usage: %s | System CPU Usage: %s",
                    cpuTimeStr, processLoadStr, systemLoadStr);
        }

        return "Process CPU Time: N/A | Process CPU Usage: N/A | System CPU Usage: N/A";
    }

    private void logStepResults(int repetition, Map<String, BenchmarkResult> results,
            Map<String, ResultWithCpu> cpuResults) {
        log("");
        logf("Repetition %d results (raw):", repetition);
        for (BenchmarkStrategy strategy : strategies) {
            String name = strategy.getName();
            BenchmarkResult selected = results.get(name + "_selected");
            BenchmarkResult full = results.get(name + "_full");
            ResultWithCpu selectedCpu = cpuResults.get(name + "_selected");
            ResultWithCpu fullCpu = cpuResults.get(name + "_full");

            logf("- %s X(10): %.0f ms (%.2f s), %,d rec/s | CPU %s (%s)",
                    name,
                    selected.serializationTimeMs(),
                    selected.serializationTimeSeconds(),
                    selected.serializationThroughput(),
                    formatCpuTime(selectedCpu.cpuTimeSeconds),
                    formatCpuUsage(selectedCpu.cpuUsagePercent));
            logf("- %s Y(250): %.0f ms (%.2f s), %,d rec/s | CPU %s (%s)",
                    name,
                    full.serializationTimeMs(),
                    full.serializationTimeSeconds(),
                    full.serializationThroughput(),
                    formatCpuTime(fullCpu.cpuTimeSeconds),
                    formatCpuUsage(fullCpu.cpuUsagePercent));
        }
    }

    public static void main(String[] args) {
        try {
            String schemaPath = "src/main/avro/TestRecord.avsc";
            String logFile = "benchmark_results.log";

            File schemaFile = new File(schemaPath);
            if (!schemaFile.exists()) {
                System.err.println("Error: Avro schema not found at " + schemaPath);
                System.exit(1);
            }

            Schema avroSchema = AvroBenchmark.loadSchema(schemaPath);

            List<FieldSpec> selectedFields = generateSelectedFields();

            // Initialize Strategies
            List<BenchmarkStrategy> strategies = new ArrayList<>();
            strategies.add(new AvroBenchmark(avroSchema, selectedFields));
            strategies.add(new ProtobufBenchmark(selectedFields));
            strategies.add(new SplitBenchmark(selectedFields));

            System.out.println("Initialized strategies: "
                    + strategies.stream().map(BenchmarkStrategy::getName).collect(Collectors.joining(", ")));

            BenchmarkSuite suite = new BenchmarkSuite(logFile, strategies, selectedFields);
            suite.run();

            System.out.println("\nResults written to: " + logFile);
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<FieldSpec> generateSelectedFields() {
        Random random = new Random(FIELD_SELECTION_SEED);
        Set<Integer> positions = new HashSet<>();
        while (positions.size() < SELECTED_FIELD_COUNT) {
            positions.add(random.nextInt(DataGenerator.TOTAL_FIELDS));
        }

        return positions.stream()
                .sorted()
                .map(BenchmarkSuite::positionToFieldSpec)
                .collect(Collectors.toList());
    }

    private static FieldSpec positionToFieldSpec(int position) {
        if (position < DataGenerator.INT_FIELDS) {
            int index = position;
            return new FieldSpec(FieldType.INT, index, position, position + 1, "int_" + index);
        }

        int longStart = DataGenerator.INT_FIELDS;
        int stringStart = DataGenerator.INT_FIELDS + DataGenerator.LONG_FIELDS;

        if (position < stringStart) {
            int index = position - longStart;
            return new FieldSpec(FieldType.LONG, index, position, position + 1, "long_" + index);
        }

        int index = position - stringStart;
        return new FieldSpec(FieldType.STRING, index, position, position + 1, "str_" + index);
    }

    private static ResultWithCpu measureWithCpu(Measurement measurement) throws IOException {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        boolean cpuTimeEnabled = threadBean.isThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled();
        if (!threadBean.isThreadCpuTimeEnabled() && threadBean.isThreadCpuTimeSupported()) {
            threadBean.setThreadCpuTimeEnabled(true);
            cpuTimeEnabled = true;
        }

        long startWall = System.nanoTime();
        long startCpu = cpuTimeEnabled ? threadBean.getCurrentThreadCpuTime() : -1L;
        BenchmarkResult result = measurement.run();
        long endWall = System.nanoTime();
        long endCpu = cpuTimeEnabled ? threadBean.getCurrentThreadCpuTime() : -1L;

        long wallNanos = endWall - startWall;
        long cpuNanos = cpuTimeEnabled ? Math.max(0, endCpu - startCpu) : -1L;
        double cpuTimeSeconds = cpuNanos >= 0 ? cpuNanos / 1_000_000_000.0 : -1.0;
        double cpuUsagePercent = (cpuNanos >= 0 && wallNanos > 0)
                ? Math.min(100.0, (cpuNanos * 100.0) / wallNanos)
                : -1.0;

        return new ResultWithCpu(result, cpuTimeSeconds, cpuUsagePercent);
    }

    private CpuStats calculateCpuAverage(List<Map<String, ResultWithCpu>> results, String key) {
        List<ResultWithCpu> keyResults = results.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(r -> r.result.serializationTimeSeconds()))
                .collect(Collectors.toList());

        if (keyResults.size() >= 3) {
            keyResults = keyResults.subList(1, keyResults.size() - 1);
        }

        double avgCpuTime = keyResults.stream()
                .mapToDouble(r -> r.cpuTimeSeconds)
                .filter(v -> v >= 0)
                .average()
                .orElse(-1.0);

        double avgCpuUsage = keyResults.stream()
                .mapToDouble(r -> r.cpuUsagePercent)
                .filter(v -> v >= 0)
                .average()
                .orElse(-1.0);

        return new CpuStats(avgCpuTime, avgCpuUsage);
    }

    private record StepResults(
            Map<String, BenchmarkResult> results,
            Map<String, ResultWithCpu> cpuResults) {
    }

    private record ResultWithCpu(
            BenchmarkResult result,
            double cpuTimeSeconds,
            double cpuUsagePercent) {
    }

    private record CpuStats(
            double cpuTimeSeconds,
            double cpuUsagePercent) {
    }

    @FunctionalInterface
    private interface Measurement {
        BenchmarkResult run() throws IOException;
    }

    private static String formatCpuTime(CpuStats stats) {
        if (stats == null || stats.cpuTimeSeconds < 0) {
            return "N/A";
        }
        return String.format("%.2f", stats.cpuTimeSeconds);
    }

    private static String formatCpuUsage(CpuStats stats) {
        if (stats == null || stats.cpuUsagePercent < 0) {
            return "N/A";
        }
        return String.format("%.1f", stats.cpuUsagePercent);
    }

    private static String formatCpuTime(double cpuTimeSeconds) {
        return cpuTimeSeconds < 0 ? "N/A" : String.format("%.2f s", cpuTimeSeconds);
    }

    private static String formatCpuUsage(double cpuUsagePercent) {
        return cpuUsagePercent < 0 ? "N/A" : String.format("%.1f%%", cpuUsagePercent);
    }
}
