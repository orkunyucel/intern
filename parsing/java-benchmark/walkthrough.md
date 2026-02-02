# Benchmark Results: Partial vs Full Field Parsing

This benchmark measured the end-to-end performance of three strategies (Split, Avro, Protobuf) for processing 1 million records. The goal was to compare the efficiency of extracting a subset of fields (10 out of 250) versus parsing the entire dataset.

## Execution Context

- **Environment**: MacBook Pro (Apple Silicon), 12GB Heap (`-Xmx12g`)
- **Dataset**: 1 Million records, 250 fields each.
- **Scenario X (Partial)**: Extract 10 specific fields and output as pipe-delimited string.
- **Scenario Y (Full)**: Parse all 250 fields.

## Results Table

### X (10 fields) - Partial Parsing

| Method   | Time (ms) | Time (s) | Throughput (rec/s) | CPU Time (s) | CPU Usage (%) |
|----------|-----------|----------|--------------------|--------------|---------------|
| **Split**| 3,507     | 3.51     | **285,140**        | 3.49         | 99.6%         |
| Protobuf | 17,955    | 17.95    | 55,695             | 17.32        | 96.5%         |
| Avro     | 25,601    | 25.60    | 39,061             | 23.57        | 92.1%         |

### Y (250 fields) - Full Parsing

| Method   | Time (ms) | Time (s) | Throughput (rec/s) | CPU Time (s) | CPU Usage (%) |
|----------|-----------|----------|--------------------|--------------|---------------|
| **Split**| 7,819     | 7.82     | **127,889**        | 7.33         | 93.8%         |
| Protobuf | 16,393    | 16.39    | 61,003             | 16.03        | 97.8%         |
| Avro     | 23,669    | 23.67    | 42,249             | 23.28        | 98.3%         |

## Analysis

### 1. The "Split" Advantage (In this specific setup)

`Split` performed significantly better (3.5s vs 17s/25s) for the partial scenario.

- **Reason**: The benchmark setup is "End-to-End" starting from a `String` (pipe-delimited).
- **Split**: Simply splits the string and picks indices. Very low overhead.
- **Avro/Protobuf**: Must first *parse* the pipe-string, *serialize* it to binary (Simulating Producer), and then *deserialize* it (Consumer). They are doing double the work (Encode + Decode) compared to Split (Just Decode/Split).

### 2. Partial vs Full Parsing Efficiency

- **Split**: 3.5s (Partial) vs 7.8s (Full). **~2.2x faster** when parsing fewer fields. This confirms that `String.split` overhead is proportional to the number of splits or array creation if optimized, or simply that reconstructing a smaller string is cheaper.
- **Protobuf**: 17.9s (Partial) vs 16.4s (Full). Surprisingly, partial parsing was slightly *slower* or similar. This suggests that the overhead of "Generic" parsing or skipping fields manually in the implementation might not be fully optimized for "skipping", or the Serialization cost dominates the total time so much that the parsing difference is negligible.
- **Avro**: 25.6s (Partial) vs 23.7s (Full). Similar to Protobuf, the overhead of the library and the serialization step is the bottleneck, not the number of fields extracted.

## Conclusion

For a pipeline where raw data is already failing into a text format (CSV/Pipe), using simple String manipulation (`Split`) is vastly more efficient than converting to a binary format (Avro/Proto) just for the sake of processing, unless the binary format is required for downstream storage or network transmission.

### 3. Theoretical Performance (If Input Was Binary)

If the input data was already in native binary format (e.g., consumed directly from a Kafka topic storing Avro/Protobuf):

- **Protobuf/Avro**: would likely be **much faster (~1-2s)** than Split. They would skip parsing the entire text and "jump" directly to the required fields using header tags or projection schemas.
- **Split**: would be slower because it would require decoding binary to text first, then splitting.

### 4. Kafka Native "Partial Deserialization"

The "special Kafka method" refers to **Reader Schema Projection** (Avro) or selective deserialization.

- Our benchmark **already simulates this**:
  - **Avro**: Used a `selectedSchema` (Projection) to only read 10 fields.
  - **Protobuf**: Used manual `skipField()` to ignore unwanted tags.
- Even with this optimization, the **Serialization overhead (String -> Binary)** in this test setup outweighed the gains from partial reading.
