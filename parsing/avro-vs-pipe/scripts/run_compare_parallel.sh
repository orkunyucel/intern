#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.parallel.yml"
LOG_DIR="$ROOT_DIR/logs"
RUNS_DIR="$LOG_DIR/runs"

RECORD_COUNT="${RECORD_COUNT:-1000000}"
RUN_TIMEOUT_SECONDS="${RUN_TIMEOUT_SECONDS:-7200}"
PHASE_METRICS_ENABLED="${PHASE_METRICS_ENABLED:-true}"
POLL_TIMEOUT_MS="${POLL_TIMEOUT_MS:-200}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d_%H%M%S)}"

PIPE_TOPIC_BASE="${PIPE_TOPIC_BASE:-RAW_PIPE}"
AVRO_TOPIC_BASE="${AVRO_TOPIC_BASE:-AVRO_FULL}"
PIPE_TOPIC_NAME="${PIPE_TOPIC_BASE}_${RUN_ID}"
AVRO_TOPIC_NAME="${AVRO_TOPIC_BASE}_${RUN_ID}"

ensure_java17() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    :
  elif [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi

  if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Java 17 bulunamadi. Lutfen JDK 17 kur ve JAVA_HOME ayarla." >&2
    exit 1
  fi

  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"

  local spec_version
  spec_version="$(java -XshowSettings:properties -version 2>&1 | awk -F= '/^ *java\.specification\.version = /{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2); print $2; exit}')"
  if [[ "$spec_version" != "17" ]]; then
    echo "Yanlis Java surumu: $spec_version (beklenen: 17)" >&2
    java -version >&2 || true
    exit 1
  fi
}

epoch_ms() {
  perl -MTime::HiRes=time -e 'printf "%.0f\n", time()*1000'
}

wait_for_container_exit() {
  local container_name="$1"
  local deadline_epoch="$2"

  while true; do
    local status
    status="$(docker inspect -f '{{.State.Status}}' "$container_name" 2>/dev/null || echo "missing")"
    if [[ "$status" == "exited" ]]; then
      docker inspect -f '{{.State.ExitCode}}' "$container_name"
      return 0
    fi

    if (( $(date +%s) >= deadline_epoch )); then
      echo "Timed out waiting for $container_name to exit." >&2
      return 1
    fi
    sleep 2
  done
}

wait_for_file() {
  local file_path="$1"
  local deadline_epoch="$2"
  while [[ ! -s "$file_path" ]]; do
    if (( $(date +%s) >= deadline_epoch )); then
      echo "Expected metrics file not found in time: $file_path" >&2
      return 1
    fi
    sleep 1
  done
}

wait_for_kafka_ready() {
  local deadline_epoch="$1"
  while true; do
    if docker compose -f "$COMPOSE_FILE" exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --list >/dev/null 2>&1"; then
      return 0
    fi
    if (( $(date +%s) >= deadline_epoch )); then
      echo "Kafka CLI timeout" >&2
      return 1
    fi
    sleep 2
  done
}

reset_topic() {
  local topic="$1"
  docker compose -f "$COMPOSE_FILE" exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --delete --if-exists --topic '$topic' >/dev/null 2>&1 || true"
  sleep 2
  docker compose -f "$COMPOSE_FILE" exec -T kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic '$topic' --partitions 1 --replication-factor 1 >/dev/null"
}

metric_value() {
  local file_path="$1"
  local metric_key="$2"
  awk -F= -v key="$metric_key" '$1 == key { print $2 }' "$file_path"
}

min_of_3() {
  printf "%s\n%s\n%s\n" "$1" "$2" "$3" | sort -n | head -n1
}

max_of_3() {
  printf "%s\n%s\n%s\n" "$1" "$2" "$3" | sort -n | tail -n1
}

ensure_java17

export RUN_ID
export RECORD_COUNT
export PHASE_METRICS_ENABLED
export POLL_TIMEOUT_MS
export PIPE_TOPIC_NAME
export AVRO_TOPIC_NAME

mkdir -p "$RUNS_DIR/$RUN_ID"
rm -f "$LOG_DIR/compare_parallel_summary.kv" "$LOG_DIR/compare_parallel_flow.md"

cleanup() {
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

runner_start_ms="$(epoch_ms)"

echo "[1/6] Build..."
mvn_build_start_ms="$(epoch_ms)"
mvn -f "$ROOT_DIR/pom.xml" -pl app -am clean package
mvn_build_end_ms="$(epoch_ms)"
mvn_build_wall_ms=$((mvn_build_end_ms - mvn_build_start_ms))

echo "[2/6] Start shared Kafka stack..."
docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
docker_up_start_ms="$(epoch_ms)"
docker compose -f "$COMPOSE_FILE" up -d --build zookeeper kafka
docker_up_end_ms="$(epoch_ms)"
docker_up_wall_ms=$((docker_up_end_ms - docker_up_start_ms))

deadline_epoch=$(( $(date +%s) + RUN_TIMEOUT_SECONDS ))
wait_for_kafka_ready "$deadline_epoch"

run_case() {
  local case_name="$1"
  local topic_name="$2"
  local producer_service="$3"
  local consumer1_service="$4"
  local consumer2_service="$5"

  echo "[case:$case_name] reset/create topic $topic_name"
  reset_topic "$topic_name"

  echo "[case:$case_name] start consumers+producer"
  docker compose -f "$COMPOSE_FILE" up -d --build "$consumer1_service" "$consumer2_service" "$producer_service"

  local producer_container c1_container c2_container
  if [[ "$case_name" == "pipe" ]]; then
    producer_container="avpp-pipe-producer"
    c1_container="avpp-pipe-consumer-1"
    c2_container="avpp-pipe-consumer-2"
  else
    producer_container="avpp-avro-producer"
    c1_container="avpp-avro-consumer-1"
    c2_container="avpp-avro-consumer-2"
  fi

  local producer_exit c1_exit c2_exit
  producer_exit="$(wait_for_container_exit "$producer_container" "$deadline_epoch")"
  c1_exit="$(wait_for_container_exit "$c1_container" "$deadline_epoch")"
  c2_exit="$(wait_for_container_exit "$c2_container" "$deadline_epoch")"

  if [[ "$producer_exit" != "0" || "$c1_exit" != "0" || "$c2_exit" != "0" ]]; then
    echo "Case failed: $case_name" >&2
    docker compose -f "$COMPOSE_FILE" logs "$producer_service" "$consumer1_service" "$consumer2_service" >&2
    exit 1
  fi

  local producer_file="$RUNS_DIR/$RUN_ID/${case_name}_producer_metrics.kv"
  local consumer1_file="$RUNS_DIR/$RUN_ID/${case_name}_consumer_1_metrics.kv"
  local consumer2_file="$RUNS_DIR/$RUN_ID/${case_name}_consumer_2_metrics.kv"
  wait_for_file "$producer_file" "$deadline_epoch"
  wait_for_file "$consumer1_file" "$deadline_epoch"
  wait_for_file "$consumer2_file" "$deadline_epoch"
}

echo "[3/6] Run case PIPE (parallel C1+C2)"
container_wait_start_ms="$(epoch_ms)"
run_case "pipe" "$PIPE_TOPIC_NAME" "pipe-producer" "pipe-consumer-1" "pipe-consumer-2"

echo "[4/6] Run case AVRO (parallel C1+C2)"
run_case "avro" "$AVRO_TOPIC_NAME" "avro-producer" "avro-consumer-1" "avro-consumer-2"
container_wait_end_ms="$(epoch_ms)"
container_wait_wall_ms=$((container_wait_end_ms - container_wait_start_ms))

echo "[5/6] Build compare summary"
summary_start_ms="$(epoch_ms)"

PIPE_PRODUCER="$RUNS_DIR/$RUN_ID/pipe_producer_metrics.kv"
PIPE_C1="$RUNS_DIR/$RUN_ID/pipe_consumer_1_metrics.kv"
PIPE_C2="$RUNS_DIR/$RUN_ID/pipe_consumer_2_metrics.kv"
AVRO_PRODUCER="$RUNS_DIR/$RUN_ID/avro_producer_metrics.kv"
AVRO_C1="$RUNS_DIR/$RUN_ID/avro_consumer_1_metrics.kv"
AVRO_C2="$RUNS_DIR/$RUN_ID/avro_consumer_2_metrics.kv"

pipe_p_start="$(metric_value "$PIPE_PRODUCER" "start_epoch_ms")"
pipe_p_end="$(metric_value "$PIPE_PRODUCER" "end_epoch_ms")"
pipe_p_cpu_time="$(metric_value "$PIPE_PRODUCER" "process_cpu_time_ms")"
pipe_p_cpu_avg="$(metric_value "$PIPE_PRODUCER" "avg_process_cpu_pct")"
pipe_p_cpu_max="$(metric_value "$PIPE_PRODUCER" "max_process_cpu_pct")"
pipe_p_arrow_1="$(metric_value "$PIPE_PRODUCER" "arrow_map_to_producer_encode_ms")"
pipe_p_arrow_2="$(metric_value "$PIPE_PRODUCER" "arrow_producer_to_topic_ms")"

pipe_c1_start="$(metric_value "$PIPE_C1" "start_epoch_ms")"
pipe_c1_end="$(metric_value "$PIPE_C1" "end_epoch_ms")"
pipe_c1_cpu_time="$(metric_value "$PIPE_C1" "process_cpu_time_ms")"
pipe_c1_cpu_avg="$(metric_value "$PIPE_C1" "avg_process_cpu_pct")"
pipe_c1_cpu_max="$(metric_value "$PIPE_C1" "max_process_cpu_pct")"
pipe_c1_wall="$(metric_value "$PIPE_C1" "role_wall_time_ms")"
pipe_c1_tput="$(metric_value "$PIPE_C1" "throughput_rec_per_sec")"
pipe_c1_arrow_3="$(metric_value "$PIPE_C1" "arrow_topic_to_consumer_decode_or_split_ms")"
pipe_c1_arrow_4="$(metric_value "$PIPE_C1" "arrow_topic_to_consumer_parse_selected_ms")"
pipe_c1_arrow_total="$(metric_value "$PIPE_C1" "arrow_topic_to_consumer_total_ms")"

pipe_c2_start="$(metric_value "$PIPE_C2" "start_epoch_ms")"
pipe_c2_end="$(metric_value "$PIPE_C2" "end_epoch_ms")"
pipe_c2_cpu_time="$(metric_value "$PIPE_C2" "process_cpu_time_ms")"
pipe_c2_cpu_avg="$(metric_value "$PIPE_C2" "avg_process_cpu_pct")"
pipe_c2_cpu_max="$(metric_value "$PIPE_C2" "max_process_cpu_pct")"
pipe_c2_wall="$(metric_value "$PIPE_C2" "role_wall_time_ms")"
pipe_c2_tput="$(metric_value "$PIPE_C2" "throughput_rec_per_sec")"
pipe_c2_arrow_3="$(metric_value "$PIPE_C2" "arrow_topic_to_consumer_decode_or_split_ms")"
pipe_c2_arrow_4="$(metric_value "$PIPE_C2" "arrow_topic_to_consumer_parse_selected_ms")"
pipe_c2_arrow_total="$(metric_value "$PIPE_C2" "arrow_topic_to_consumer_total_ms")"

avro_p_start="$(metric_value "$AVRO_PRODUCER" "start_epoch_ms")"
avro_p_end="$(metric_value "$AVRO_PRODUCER" "end_epoch_ms")"
avro_p_cpu_time="$(metric_value "$AVRO_PRODUCER" "process_cpu_time_ms")"
avro_p_cpu_avg="$(metric_value "$AVRO_PRODUCER" "avg_process_cpu_pct")"
avro_p_cpu_max="$(metric_value "$AVRO_PRODUCER" "max_process_cpu_pct")"
avro_p_arrow_1="$(metric_value "$AVRO_PRODUCER" "arrow_map_to_producer_encode_ms")"
avro_p_arrow_2="$(metric_value "$AVRO_PRODUCER" "arrow_producer_to_topic_ms")"

avro_c1_start="$(metric_value "$AVRO_C1" "start_epoch_ms")"
avro_c1_end="$(metric_value "$AVRO_C1" "end_epoch_ms")"
avro_c1_cpu_time="$(metric_value "$AVRO_C1" "process_cpu_time_ms")"
avro_c1_cpu_avg="$(metric_value "$AVRO_C1" "avg_process_cpu_pct")"
avro_c1_cpu_max="$(metric_value "$AVRO_C1" "max_process_cpu_pct")"
avro_c1_wall="$(metric_value "$AVRO_C1" "role_wall_time_ms")"
avro_c1_tput="$(metric_value "$AVRO_C1" "throughput_rec_per_sec")"
avro_c1_arrow_3="$(metric_value "$AVRO_C1" "arrow_topic_to_consumer_decode_or_split_ms")"
avro_c1_arrow_4="$(metric_value "$AVRO_C1" "arrow_topic_to_consumer_parse_selected_ms")"
avro_c1_arrow_total="$(metric_value "$AVRO_C1" "arrow_topic_to_consumer_total_ms")"

avro_c2_start="$(metric_value "$AVRO_C2" "start_epoch_ms")"
avro_c2_end="$(metric_value "$AVRO_C2" "end_epoch_ms")"
avro_c2_cpu_time="$(metric_value "$AVRO_C2" "process_cpu_time_ms")"
avro_c2_cpu_avg="$(metric_value "$AVRO_C2" "avg_process_cpu_pct")"
avro_c2_cpu_max="$(metric_value "$AVRO_C2" "max_process_cpu_pct")"
avro_c2_wall="$(metric_value "$AVRO_C2" "role_wall_time_ms")"
avro_c2_tput="$(metric_value "$AVRO_C2" "throughput_rec_per_sec")"
avro_c2_arrow_3="$(metric_value "$AVRO_C2" "arrow_topic_to_consumer_decode_or_split_ms")"
avro_c2_arrow_4="$(metric_value "$AVRO_C2" "arrow_topic_to_consumer_parse_selected_ms")"
avro_c2_arrow_total="$(metric_value "$AVRO_C2" "arrow_topic_to_consumer_total_ms")"

pipe_case_start="$(min_of_3 "$pipe_p_start" "$pipe_c1_start" "$pipe_c2_start")"
pipe_case_end="$(max_of_3 "$pipe_p_end" "$pipe_c1_end" "$pipe_c2_end")"
pipe_case_e2e=$((pipe_case_end - pipe_case_start))
pipe_case_cpu_total=$((pipe_p_cpu_time + pipe_c1_cpu_time + pipe_c2_cpu_time))
pipe_case_tput="$(awk -v records="$RECORD_COUNT" -v ms="$pipe_case_e2e" 'BEGIN { if (ms > 0) printf "%.4f", records / (ms/1000.0); else print "0.0000" }')"
pipe_case_c1_c2_wall_max="$(awk -v a="$pipe_c1_wall" -v b="$pipe_c2_wall" 'BEGIN { if (a > b) print a; else print b }')"

avro_case_start="$(min_of_3 "$avro_p_start" "$avro_c1_start" "$avro_c2_start")"
avro_case_end="$(max_of_3 "$avro_p_end" "$avro_c1_end" "$avro_c2_end")"
avro_case_e2e=$((avro_case_end - avro_case_start))
avro_case_cpu_total=$((avro_p_cpu_time + avro_c1_cpu_time + avro_c2_cpu_time))
avro_case_tput="$(awk -v records="$RECORD_COUNT" -v ms="$avro_case_e2e" 'BEGIN { if (ms > 0) printf "%.4f", records / (ms/1000.0); else print "0.0000" }')"
avro_case_c1_c2_wall_max="$(awk -v a="$avro_c1_wall" -v b="$avro_c2_wall" 'BEGIN { if (a > b) print a; else print b }')"

pipe_vs_avro_e2e_delta_ms=$((avro_case_e2e - pipe_case_e2e))
pipe_vs_avro_e2e_delta_pct="$(awk -v d="$pipe_vs_avro_e2e_delta_ms" -v b="$pipe_case_e2e" 'BEGIN { if (b > 0) printf "%.4f", (d*100.0)/b; else print "0.0000" }')"

summary_end_ms="$(epoch_ms)"
summary_wall_ms=$((summary_end_ms - summary_start_ms))
runner_end_ms="$(epoch_ms)"
runner_total_ms=$((runner_end_ms - runner_start_ms))

cat > "$LOG_DIR/compare_parallel_summary.kv" <<SUMMARY
run_id=$RUN_ID
record_count=$RECORD_COUNT
shared_kafka=true
mode=parallel_c1_c2
pipe_topic_name=$PIPE_TOPIC_NAME
avro_topic_name=$AVRO_TOPIC_NAME
maven_build_wall_ms=$mvn_build_wall_ms
docker_up_build_wall_ms=$docker_up_wall_ms
summary_build_wall_ms=$summary_wall_ms
container_wait_wall_ms=$container_wait_wall_ms
runner_total_wall_ms=$runner_total_ms

pipe_case_e2e_wall_time_ms=$pipe_case_e2e
pipe_case_total_process_cpu_time_ms=$pipe_case_cpu_total
pipe_case_throughput_rec_per_sec=$pipe_case_tput
pipe_case_c1_c2_wall_max_ms=$pipe_case_c1_c2_wall_max
pipe_producer_arrow_map_to_producer_encode_ms=$pipe_p_arrow_1
pipe_producer_arrow_producer_to_topic_ms=$pipe_p_arrow_2
pipe_consumer1_arrow_topic_to_consumer_decode_or_split_ms=$pipe_c1_arrow_3
pipe_consumer1_arrow_topic_to_consumer_parse_selected_ms=$pipe_c1_arrow_4
pipe_consumer1_arrow_topic_to_consumer_total_ms=$pipe_c1_arrow_total
pipe_consumer2_arrow_topic_to_consumer_decode_or_split_ms=$pipe_c2_arrow_3
pipe_consumer2_arrow_topic_to_consumer_parse_selected_ms=$pipe_c2_arrow_4
pipe_consumer2_arrow_topic_to_consumer_total_ms=$pipe_c2_arrow_total
pipe_consumer1_role_wall_time_ms=$pipe_c1_wall
pipe_consumer2_role_wall_time_ms=$pipe_c2_wall
pipe_consumer1_process_cpu_time_ms=$pipe_c1_cpu_time
pipe_consumer2_process_cpu_time_ms=$pipe_c2_cpu_time
pipe_consumer1_avg_process_cpu_pct=$pipe_c1_cpu_avg
pipe_consumer2_avg_process_cpu_pct=$pipe_c2_cpu_avg
pipe_consumer1_max_process_cpu_pct=$pipe_c1_cpu_max
pipe_consumer2_max_process_cpu_pct=$pipe_c2_cpu_max

avro_case_e2e_wall_time_ms=$avro_case_e2e
avro_case_total_process_cpu_time_ms=$avro_case_cpu_total
avro_case_throughput_rec_per_sec=$avro_case_tput
avro_case_c1_c2_wall_max_ms=$avro_case_c1_c2_wall_max
avro_producer_arrow_map_to_producer_encode_ms=$avro_p_arrow_1
avro_producer_arrow_producer_to_topic_ms=$avro_p_arrow_2
avro_consumer1_arrow_topic_to_consumer_decode_or_split_ms=$avro_c1_arrow_3
avro_consumer1_arrow_topic_to_consumer_parse_selected_ms=$avro_c1_arrow_4
avro_consumer1_arrow_topic_to_consumer_total_ms=$avro_c1_arrow_total
avro_consumer2_arrow_topic_to_consumer_decode_or_split_ms=$avro_c2_arrow_3
avro_consumer2_arrow_topic_to_consumer_parse_selected_ms=$avro_c2_arrow_4
avro_consumer2_arrow_topic_to_consumer_total_ms=$avro_c2_arrow_total
avro_consumer1_role_wall_time_ms=$avro_c1_wall
avro_consumer2_role_wall_time_ms=$avro_c2_wall
avro_consumer1_process_cpu_time_ms=$avro_c1_cpu_time
avro_consumer2_process_cpu_time_ms=$avro_c2_cpu_time
avro_consumer1_avg_process_cpu_pct=$avro_c1_cpu_avg
avro_consumer2_avg_process_cpu_pct=$avro_c2_cpu_avg
avro_consumer1_max_process_cpu_pct=$avro_c1_cpu_max
avro_consumer2_max_process_cpu_pct=$avro_c2_cpu_max

pipe_vs_avro_e2e_delta_ms=$pipe_vs_avro_e2e_delta_ms
pipe_vs_avro_e2e_delta_pct=$pipe_vs_avro_e2e_delta_pct
SUMMARY

cat > "$LOG_DIR/compare_parallel_flow.md" <<FLOW
# Avro vs Pipe Parallel Flow (Run: $RUN_ID)

~~~mermaid
%%{init: {'flowchart': {'useMaxWidth': false, 'nodeSpacing': 70, 'rankSpacing': 80}}}%%
flowchart LR
  subgraph P["Pipe Senaryosu (Parallel C1 + C2)"]
    direction LR
    P1["Map/Array Obj (shared sabit)"] -->|"time=${pipe_p_arrow_1}ms cpu_time=${pipe_p_cpu_time}ms cpu_avg=${pipe_p_cpu_avg}% cpu_max=${pipe_p_cpu_max}% gpu=N/A"| P2["Producer: pipe encode (250)"]
    P2 -->|"time=${pipe_p_arrow_2}ms cpu_time=${pipe_p_cpu_time}ms cpu_avg=${pipe_p_cpu_avg}% cpu_max=${pipe_p_cpu_max}% gpu=N/A"| P3["Kafka Topic: ${PIPE_TOPIC_NAME} (same broker)"]
    P3 -->|"time=${pipe_c1_arrow_3}ms cpu_time=${pipe_c1_cpu_time}ms cpu_avg=${pipe_c1_cpu_avg}% cpu_max=${pipe_c1_cpu_max}% gpu=N/A"| P4["Consumer-1: split250"]
    P4 -->|"time=${pipe_c1_arrow_4}ms cpu_time=${pipe_c1_cpu_time}ms cpu_avg=${pipe_c1_cpu_avg}% cpu_max=${pipe_c1_cpu_max}% gpu=N/A"| P5["Consumer-1: parse250 + select10"]
    P3 -->|"time=${pipe_c2_arrow_3}ms cpu_time=${pipe_c2_cpu_time}ms cpu_avg=${pipe_c2_cpu_avg}% cpu_max=${pipe_c2_cpu_max}% gpu=N/A"| P6["Consumer-2: split250"]
    P6 -->|"time=${pipe_c2_arrow_4}ms cpu_time=${pipe_c2_cpu_time}ms cpu_avg=${pipe_c2_cpu_avg}% cpu_max=${pipe_c2_cpu_max}% gpu=N/A"| P7["Consumer-2: parse250 (full)"]
  end

  subgraph A["Avro Senaryosu (Parallel C1 + C2)"]
    direction LR
    A1["Map/Array Obj (shared sabit)"] -->|"time=${avro_p_arrow_1}ms cpu_time=${avro_p_cpu_time}ms cpu_avg=${avro_p_cpu_avg}% cpu_max=${avro_p_cpu_max}% gpu=N/A"| A2["Producer: avro encode (250)"]
    A2 -->|"time=${avro_p_arrow_2}ms cpu_time=${avro_p_cpu_time}ms cpu_avg=${avro_p_cpu_avg}% cpu_max=${avro_p_cpu_max}% gpu=N/A"| A3["Kafka Topic: ${AVRO_TOPIC_NAME} (same broker)"]
    A3 -->|"time=${avro_c1_arrow_3}ms cpu_time=${avro_c1_cpu_time}ms cpu_avg=${avro_c1_cpu_avg}% cpu_max=${avro_c1_cpu_max}% gpu=N/A"| A4["Consumer-1: reader decode10"]
    A4 -->|"time=${avro_c1_arrow_4}ms cpu_time=${avro_c1_cpu_time}ms cpu_avg=${avro_c1_cpu_avg}% cpu_max=${avro_c1_cpu_max}% gpu=N/A"| A5["Consumer-1: parse10"]
    A3 -->|"time=${avro_c2_arrow_3}ms cpu_time=${avro_c2_cpu_time}ms cpu_avg=${avro_c2_cpu_avg}% cpu_max=${avro_c2_cpu_max}% gpu=N/A"| A6["Consumer-2: full decode250"]
    A6 -->|"time=${avro_c2_arrow_4}ms cpu_time=${avro_c2_cpu_time}ms cpu_avg=${avro_c2_cpu_avg}% cpu_max=${avro_c2_cpu_max}% gpu=N/A"| A7["Consumer-2: parse250 (full)"]
  end

  P5 --> E["E2E Parallel\\nPipe=${pipe_case_e2e}ms\\nAvro=${avro_case_e2e}ms\\nDelta=${pipe_vs_avro_e2e_delta_ms}ms (${pipe_vs_avro_e2e_delta_pct}%)"]
  P7 --> E
  A5 --> E
  A7 --> E
~~~
FLOW

echo "[6/6] Done"
echo "Summary: $LOG_DIR/compare_parallel_summary.kv"
echo "Flow:    $LOG_DIR/compare_parallel_flow.md"
