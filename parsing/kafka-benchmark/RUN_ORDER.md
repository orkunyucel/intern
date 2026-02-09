# Benchmark Run Order (Skeleton)

Run scenarios separately in this order:

1. `01-oldschool-pipe`
2. `02-avro-reader-schema`
3. `03-protobuf-selective-parse`

Common benchmark assumptions:
- `record_count = 1000000`
- `slot_count = 250`

Each scenario writes its own log file:
- `scenarios/<scenario>/logs/benchmark_results.log`
