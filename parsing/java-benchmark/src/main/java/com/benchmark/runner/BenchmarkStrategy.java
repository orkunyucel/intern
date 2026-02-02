package com.benchmark.runner;

import com.benchmark.model.BenchmarkResult;
import java.io.IOException;
import java.util.List;

public interface BenchmarkStrategy {
    BenchmarkResult measureSelected(List<String> records) throws IOException;

    BenchmarkResult measureFull(List<String> records) throws IOException;

    String getName();
}
