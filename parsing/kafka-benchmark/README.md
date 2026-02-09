# Kafka Benchmark

Bu repo 3 farkli parsing/serialization senaryosunu ayni benchmark modeliyle olcmek icin kullanilir.

- Runtime: Java 17
- Broker: Apache Kafka (Zookeeper mod)
- Veri modeli: 250 alanli shared static map/array obje
- Karsilastirma hedefi:
- Consumer-1: 10 alan
- Consumer-2: 250 alan

## Senaryolar

1. `01-oldschool-pipe`
- Producer: pipe encode (250)
- Topic: `RAW_PIPE`
- Consumer-1: split + 10 alan parse
- Consumer-2: split + 250 alan parse

2. `02-avro-reader-schema`
- Producer: Avro encode (250)
- Topic: `AVRO_FULL`
- Consumer-1: reader schema ile 10 alan decode
- Consumer-2: full schema ile 250 alan decode

3. `03-protobuf-selective-parse`
- Producer: Protobuf encode (250)
- Topic: `PROTO_FULL`
- Consumer-1: selective parse (10 alan, skipField)
- Consumer-2: full parse (250 alan)

## Log Formati (Tum Senaryolarda Ayni)

Her senaryo su dosyaya yazar:
- `scenarios/<scenario>/logs/benchmark_results.log`

Bloklar:
1. `ENV`
2. `RUNNER_TIMING`
3. `ROLE_SUMMARY`
4. `ROLE_PHASES`
5. `ROLE_OBJECT_STATS`
6. `SCENARIO_FINAL`

### 1) RUNNER_TIMING
Senaryo orchestration maliyeti:
- `maven_build_wall_ms`
- `docker_up_build_wall_ms`
- `container_wait_wall_ms`
- `metrics_aggregation_wall_ms`
- `runner_total_wall_ms`

### 2) ROLE_SUMMARY
Role bazli ana performans:
- `records_processed`
- `process_cpu_time_ms`
- `avg_process_cpu_pct`
- `max_process_cpu_pct`
- `wall_time_ms`
- `throughput_rec_per_sec`
- `start_epoch_ms`
- `end_epoch_ms`

### 3) ROLE_PHASES
Kritik phase zamanlari:
- `phase_encode_or_build_ms`
- `phase_decode_or_split_ms`
- `phase_parse_selected_or_full_ms`
- `phase_role_total_loop_ms`

### 4) ROLE_OBJECT_STATS
Obje ve payload istatistikleri:
- `object_create_count`
- `object_encode_count`
- `object_decode_count`
- `object_skip_count` (proto selective role icin anlamli)
- `payload_total_bytes`
- `payload_avg_bytes`
- `payload_min_bytes`
- `payload_max_bytes`

### 5) SCENARIO_FINAL
Senaryo total cost ozeti:
- `e2e_wall_time_ms`
- `total_process_cpu_time_ms`
- `effective_e2e_throughput_rec_per_sec`
- `consumer1_vs_consumer2_cpu_ratio`
- `consumer1_vs_consumer2_wall_ratio`
- `scenario_avg_payload_bytes`
- `scenario_total_cost_wall_ms`

## Calisma Sirasi

Ayrintili sira:
- `RUN_ORDER.md`

Tipik sira:
1. `scenarios/01-oldschool-pipe/run_oldschool.sh`
2. `scenarios/02-avro-reader-schema/run_avro_reader.sh`
3. `scenarios/03-protobuf-selective-parse/run_protobuf_selective.sh`

Opsiyonel env:
- `RECORD_COUNT` (default `1000000`)
- `TOPIC_NAME`
- `RUN_TIMEOUT_SECONDS`
- `PHASE_METRICS_ENABLED` (default `true`)
