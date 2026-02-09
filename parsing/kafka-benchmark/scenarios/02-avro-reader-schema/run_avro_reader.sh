#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCENARIO_DIR="$ROOT_DIR/scenarios/02-avro-reader-schema"
LOG_DIR="$SCENARIO_DIR/logs"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.scenario2.yml"

TOPIC_NAME="${TOPIC_NAME:-AVRO_FULL}"
RECORD_COUNT="${RECORD_COUNT:-1000000}"
RUN_TIMEOUT_SECONDS="${RUN_TIMEOUT_SECONDS:-7200}"

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

ensure_java17

PRODUCER_METRICS_FILE="$LOG_DIR/producer_metrics.kv"
CONSUMER1_METRICS_FILE="$LOG_DIR/consumer_1_metrics.kv"
CONSUMER2_METRICS_FILE="$LOG_DIR/consumer_2_metrics.kv"
BENCHMARK_LOG_FILE="$LOG_DIR/benchmark_results.log"

epoch_ms() {
  perl -MTime::HiRes=time -e 'printf "%.0f\n", time()*1000'
}

mkdir -p "$LOG_DIR"
rm -f "$PRODUCER_METRICS_FILE" "$CONSUMER1_METRICS_FILE" "$CONSUMER2_METRICS_FILE" "$BENCHMARK_LOG_FILE"

cleanup() {
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

runner_start_epoch_ms="$(epoch_ms)"

echo "[1/5] Maven build (Java 17 modules)..."
maven_build_start_ms="$(epoch_ms)"
mvn -f "$ROOT_DIR/pom.xml" -pl shared-model,scenarios/02-avro-reader-schema -am clean package
maven_build_end_ms="$(epoch_ms)"
maven_build_wall_ms=$((maven_build_end_ms - maven_build_start_ms))

echo "[2/5] Reset docker stack..."
docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true

echo "[3/5] Start stack..."
export TOPIC_NAME
export RECORD_COUNT
docker_up_start_ms="$(epoch_ms)"
docker compose -f "$COMPOSE_FILE" up -d --build zookeeper kafka producer consumer-1 consumer-2
docker_up_end_ms="$(epoch_ms)"
docker_up_build_wall_ms=$((docker_up_end_ms - docker_up_start_ms))

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

    if (( "$(date +%s)" >= deadline_epoch )); then
      echo "Timed out waiting for $container_name to exit." >&2
      return 1
    fi
    sleep 2
  done
}

deadline_epoch=$(( $(date +%s) + RUN_TIMEOUT_SECONDS ))

wait_start_ms="$(epoch_ms)"
echo "[4/5] Wait for producer/consumers to finish..."
producer_exit_code="$(wait_for_container_exit "kb2-producer" "$deadline_epoch")"
consumer1_exit_code="$(wait_for_container_exit "kb2-consumer-1" "$deadline_epoch")"
consumer2_exit_code="$(wait_for_container_exit "kb2-consumer-2" "$deadline_epoch")"
wait_end_ms="$(epoch_ms)"
container_wait_wall_ms=$((wait_end_ms - wait_start_ms))

if [[ "$producer_exit_code" != "0" || "$consumer1_exit_code" != "0" || "$consumer2_exit_code" != "0" ]]; then
  echo "At least one app container failed." >&2
  docker compose -f "$COMPOSE_FILE" logs producer consumer-1 consumer-2 >&2
  exit 1
fi

wait_for_file() {
  local file_path="$1"
  local deadline_epoch="$2"
  while [[ ! -s "$file_path" ]]; do
    if (( "$(date +%s)" >= deadline_epoch )); then
      echo "Expected metrics file not found in time: $file_path" >&2
      return 1
    fi
    sleep 1
  done
}

wait_for_file "$PRODUCER_METRICS_FILE" "$deadline_epoch"
wait_for_file "$CONSUMER1_METRICS_FILE" "$deadline_epoch"
wait_for_file "$CONSUMER2_METRICS_FILE" "$deadline_epoch"

metrics_aggregation_start_ms="$(epoch_ms)"

metric_value() {
  local file_path="$1"
  local metric_key="$2"
  awk -F= -v key="$metric_key" '$1 == key { print $2 }' "$file_path"
}

producer_start="$(metric_value "$PRODUCER_METRICS_FILE" "start_epoch_ms")"
producer_end="$(metric_value "$PRODUCER_METRICS_FILE" "end_epoch_ms")"
producer_cpu="$(metric_value "$PRODUCER_METRICS_FILE" "process_cpu_time_ms")"
producer_cpu_avg="$(metric_value "$PRODUCER_METRICS_FILE" "avg_process_cpu_pct")"
producer_cpu_max="$(metric_value "$PRODUCER_METRICS_FILE" "max_process_cpu_pct")"
producer_wall="$(metric_value "$PRODUCER_METRICS_FILE" "wall_time_ms")"
producer_tput="$(metric_value "$PRODUCER_METRICS_FILE" "throughput_rec_per_sec")"
producer_records="$(metric_value "$PRODUCER_METRICS_FILE" "records_processed")"
producer_total_payload="$(metric_value "$PRODUCER_METRICS_FILE" "total_payload_bytes")"
producer_avg_payload="$(metric_value "$PRODUCER_METRICS_FILE" "avg_payload_bytes")"
producer_min_payload="$(metric_value "$PRODUCER_METRICS_FILE" "min_payload_bytes")"
producer_max_payload="$(metric_value "$PRODUCER_METRICS_FILE" "max_payload_bytes")"
producer_phase_encode="$(metric_value "$PRODUCER_METRICS_FILE" "phase_encode_or_build_ms")"
producer_phase_decode="$(metric_value "$PRODUCER_METRICS_FILE" "phase_decode_or_split_ms")"
producer_phase_parse="$(metric_value "$PRODUCER_METRICS_FILE" "phase_parse_selected_or_full_ms")"
producer_phase_loop="$(metric_value "$PRODUCER_METRICS_FILE" "phase_role_total_loop_ms")"
producer_object_create="$(metric_value "$PRODUCER_METRICS_FILE" "object_create_count")"
producer_object_encode="$(metric_value "$PRODUCER_METRICS_FILE" "object_encode_count")"
producer_object_decode="$(metric_value "$PRODUCER_METRICS_FILE" "object_decode_count")"
producer_object_skip="$(metric_value "$PRODUCER_METRICS_FILE" "object_skip_count")"

consumer1_start="$(metric_value "$CONSUMER1_METRICS_FILE" "start_epoch_ms")"
consumer1_end="$(metric_value "$CONSUMER1_METRICS_FILE" "end_epoch_ms")"
consumer1_cpu="$(metric_value "$CONSUMER1_METRICS_FILE" "process_cpu_time_ms")"
consumer1_cpu_avg="$(metric_value "$CONSUMER1_METRICS_FILE" "avg_process_cpu_pct")"
consumer1_cpu_max="$(metric_value "$CONSUMER1_METRICS_FILE" "max_process_cpu_pct")"
consumer1_wall="$(metric_value "$CONSUMER1_METRICS_FILE" "wall_time_ms")"
consumer1_tput="$(metric_value "$CONSUMER1_METRICS_FILE" "throughput_rec_per_sec")"
consumer1_records="$(metric_value "$CONSUMER1_METRICS_FILE" "records_processed")"
consumer1_total_payload="$(metric_value "$CONSUMER1_METRICS_FILE" "total_payload_bytes")"
consumer1_avg_payload="$(metric_value "$CONSUMER1_METRICS_FILE" "avg_payload_bytes")"
consumer1_min_payload="$(metric_value "$CONSUMER1_METRICS_FILE" "min_payload_bytes")"
consumer1_max_payload="$(metric_value "$CONSUMER1_METRICS_FILE" "max_payload_bytes")"
consumer1_phase_encode="$(metric_value "$CONSUMER1_METRICS_FILE" "phase_encode_or_build_ms")"
consumer1_phase_decode="$(metric_value "$CONSUMER1_METRICS_FILE" "phase_decode_or_split_ms")"
consumer1_phase_parse="$(metric_value "$CONSUMER1_METRICS_FILE" "phase_parse_selected_or_full_ms")"
consumer1_phase_loop="$(metric_value "$CONSUMER1_METRICS_FILE" "phase_role_total_loop_ms")"
consumer1_object_create="$(metric_value "$CONSUMER1_METRICS_FILE" "object_create_count")"
consumer1_object_encode="$(metric_value "$CONSUMER1_METRICS_FILE" "object_encode_count")"
consumer1_object_decode="$(metric_value "$CONSUMER1_METRICS_FILE" "object_decode_count")"
consumer1_object_skip="$(metric_value "$CONSUMER1_METRICS_FILE" "object_skip_count")"

consumer2_start="$(metric_value "$CONSUMER2_METRICS_FILE" "start_epoch_ms")"
consumer2_end="$(metric_value "$CONSUMER2_METRICS_FILE" "end_epoch_ms")"
consumer2_cpu="$(metric_value "$CONSUMER2_METRICS_FILE" "process_cpu_time_ms")"
consumer2_cpu_avg="$(metric_value "$CONSUMER2_METRICS_FILE" "avg_process_cpu_pct")"
consumer2_cpu_max="$(metric_value "$CONSUMER2_METRICS_FILE" "max_process_cpu_pct")"
consumer2_wall="$(metric_value "$CONSUMER2_METRICS_FILE" "wall_time_ms")"
consumer2_tput="$(metric_value "$CONSUMER2_METRICS_FILE" "throughput_rec_per_sec")"
consumer2_records="$(metric_value "$CONSUMER2_METRICS_FILE" "records_processed")"
consumer2_total_payload="$(metric_value "$CONSUMER2_METRICS_FILE" "total_payload_bytes")"
consumer2_avg_payload="$(metric_value "$CONSUMER2_METRICS_FILE" "avg_payload_bytes")"
consumer2_min_payload="$(metric_value "$CONSUMER2_METRICS_FILE" "min_payload_bytes")"
consumer2_max_payload="$(metric_value "$CONSUMER2_METRICS_FILE" "max_payload_bytes")"
consumer2_phase_encode="$(metric_value "$CONSUMER2_METRICS_FILE" "phase_encode_or_build_ms")"
consumer2_phase_decode="$(metric_value "$CONSUMER2_METRICS_FILE" "phase_decode_or_split_ms")"
consumer2_phase_parse="$(metric_value "$CONSUMER2_METRICS_FILE" "phase_parse_selected_or_full_ms")"
consumer2_phase_loop="$(metric_value "$CONSUMER2_METRICS_FILE" "phase_role_total_loop_ms")"
consumer2_object_create="$(metric_value "$CONSUMER2_METRICS_FILE" "object_create_count")"
consumer2_object_encode="$(metric_value "$CONSUMER2_METRICS_FILE" "object_encode_count")"
consumer2_object_decode="$(metric_value "$CONSUMER2_METRICS_FILE" "object_decode_count")"
consumer2_object_skip="$(metric_value "$CONSUMER2_METRICS_FILE" "object_skip_count")"

min_start_epoch="$(printf "%s\n%s\n%s\n" "$producer_start" "$consumer1_start" "$consumer2_start" | sort -n | head -n 1)"
max_end_epoch="$(printf "%s\n%s\n%s\n" "$producer_end" "$consumer1_end" "$consumer2_end" | sort -n | tail -n 1)"
e2e_wall_time_ms=$((max_end_epoch - min_start_epoch))
total_process_cpu_time_ms=$((producer_cpu + consumer1_cpu + consumer2_cpu))

effective_e2e_tput="$(awk -v records="$RECORD_COUNT" -v ms="$e2e_wall_time_ms" 'BEGIN { if (ms > 0) printf "%.4f", records / (ms / 1000.0); else print "0.0000"; }')"
consumer_ratio_cpu="$(awk -v c1="$consumer1_cpu" -v c2="$consumer2_cpu" 'BEGIN { if (c2 > 0) printf "%.6f", c1 / c2; else print "0.000000"; }')"
consumer_ratio_wall="$(awk -v c1="$consumer1_wall" -v c2="$consumer2_wall" 'BEGIN { if (c2 > 0) printf "%.6f", c1 / c2; else print "0.000000"; }')"
scenario_avg_payload_bytes="$producer_avg_payload"

metrics_aggregation_end_ms="$(epoch_ms)"
metrics_aggregation_wall_ms=$((metrics_aggregation_end_ms - metrics_aggregation_start_ms))

runner_end_epoch_ms="$(epoch_ms)"
runner_total_wall_ms=$((runner_end_epoch_ms - runner_start_epoch_ms))
scenario_total_cost_wall_ms="$runner_total_wall_ms"

echo "[5/5] Write benchmark log..."
{
  echo "ENV"
  echo "scenario=avro-reader-schema"
  echo "java_runtime=eclipse-temurin:17-jre"
  echo "topic_name=$TOPIC_NAME"
  echo "record_count=$RECORD_COUNT"
  echo "slot_count=250"
  echo "consumer_1_group=kb-avro-c1"
  echo "consumer_2_group=kb-avro-c2"
  echo
  echo "RUNNER_TIMING"
  echo "runner_start_epoch_ms=$runner_start_epoch_ms"
  echo "maven_build_wall_ms=$maven_build_wall_ms"
  echo "docker_up_build_wall_ms=$docker_up_build_wall_ms"
  echo "container_wait_wall_ms=$container_wait_wall_ms"
  echo "metrics_aggregation_wall_ms=$metrics_aggregation_wall_ms"
  echo "runner_end_epoch_ms=$runner_end_epoch_ms"
  echo "runner_total_wall_ms=$runner_total_wall_ms"
  echo
  echo "ROLE_SUMMARY"
  echo "role=producer records_processed=$producer_records process_cpu_time_ms=$producer_cpu avg_process_cpu_pct=$producer_cpu_avg max_process_cpu_pct=$producer_cpu_max wall_time_ms=$producer_wall throughput_rec_per_sec=$producer_tput total_payload_bytes=$producer_total_payload avg_payload_bytes=$producer_avg_payload min_payload_bytes=$producer_min_payload max_payload_bytes=$producer_max_payload start_epoch_ms=$producer_start end_epoch_ms=$producer_end"
  echo "role=consumer-1 records_processed=$consumer1_records process_cpu_time_ms=$consumer1_cpu avg_process_cpu_pct=$consumer1_cpu_avg max_process_cpu_pct=$consumer1_cpu_max wall_time_ms=$consumer1_wall throughput_rec_per_sec=$consumer1_tput total_payload_bytes=$consumer1_total_payload avg_payload_bytes=$consumer1_avg_payload min_payload_bytes=$consumer1_min_payload max_payload_bytes=$consumer1_max_payload start_epoch_ms=$consumer1_start end_epoch_ms=$consumer1_end"
  echo "role=consumer-2 records_processed=$consumer2_records process_cpu_time_ms=$consumer2_cpu avg_process_cpu_pct=$consumer2_cpu_avg max_process_cpu_pct=$consumer2_cpu_max wall_time_ms=$consumer2_wall throughput_rec_per_sec=$consumer2_tput total_payload_bytes=$consumer2_total_payload avg_payload_bytes=$consumer2_avg_payload min_payload_bytes=$consumer2_min_payload max_payload_bytes=$consumer2_max_payload start_epoch_ms=$consumer2_start end_epoch_ms=$consumer2_end"
  echo
  echo "ROLE_PHASES"
  echo "role=producer phase_encode_or_build_ms=$producer_phase_encode phase_decode_or_split_ms=$producer_phase_decode phase_parse_selected_or_full_ms=$producer_phase_parse phase_role_total_loop_ms=$producer_phase_loop"
  echo "role=consumer-1 phase_encode_or_build_ms=$consumer1_phase_encode phase_decode_or_split_ms=$consumer1_phase_decode phase_parse_selected_or_full_ms=$consumer1_phase_parse phase_role_total_loop_ms=$consumer1_phase_loop"
  echo "role=consumer-2 phase_encode_or_build_ms=$consumer2_phase_encode phase_decode_or_split_ms=$consumer2_phase_decode phase_parse_selected_or_full_ms=$consumer2_phase_parse phase_role_total_loop_ms=$consumer2_phase_loop"
  echo
  echo "ROLE_OBJECT_STATS"
  echo "role=producer object_create_count=$producer_object_create object_encode_count=$producer_object_encode object_decode_count=$producer_object_decode object_skip_count=$producer_object_skip payload_total_bytes=$producer_total_payload payload_avg_bytes=$producer_avg_payload payload_min_bytes=$producer_min_payload payload_max_bytes=$producer_max_payload"
  echo "role=consumer-1 object_create_count=$consumer1_object_create object_encode_count=$consumer1_object_encode object_decode_count=$consumer1_object_decode object_skip_count=$consumer1_object_skip payload_total_bytes=$consumer1_total_payload payload_avg_bytes=$consumer1_avg_payload payload_min_bytes=$consumer1_min_payload payload_max_bytes=$consumer1_max_payload"
  echo "role=consumer-2 object_create_count=$consumer2_object_create object_encode_count=$consumer2_object_encode object_decode_count=$consumer2_object_decode object_skip_count=$consumer2_object_skip payload_total_bytes=$consumer2_total_payload payload_avg_bytes=$consumer2_avg_payload payload_min_bytes=$consumer2_min_payload payload_max_bytes=$consumer2_max_payload"
  echo
  echo "SCENARIO_FINAL"
  echo "e2e_wall_time_ms=$e2e_wall_time_ms"
  echo "total_process_cpu_time_ms=$total_process_cpu_time_ms"
  echo "effective_e2e_throughput_rec_per_sec=$effective_e2e_tput"
  echo "consumer1_vs_consumer2_cpu_ratio=$consumer_ratio_cpu"
  echo "consumer1_vs_consumer2_wall_ratio=$consumer_ratio_wall"
  echo "scenario_avg_payload_bytes=$scenario_avg_payload_bytes"
  echo "scenario_total_cost_wall_ms=$scenario_total_cost_wall_ms"
} > "$BENCHMARK_LOG_FILE"

echo "Benchmark completed:"
echo "$BENCHMARK_LOG_FILE"
