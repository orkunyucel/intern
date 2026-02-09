package com.benchmark.scenario2;

import com.benchmark.shared.StaticMappedObject;
import com.sun.management.OperatingSystemMXBean;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class AvroReaderSchemaMain {

    private static final String SCENARIO_NAME = "avro-reader-schema";
    private static final String DEFAULT_TOPIC = "AVRO_FULL";
    private static final int DEFAULT_RECORD_COUNT = 1_000_000;
    private static final int DEFAULT_POLL_TIMEOUT_MS = 200;
    private static final String FULL_SCHEMA_RESOURCE = "avro/reader_consumer2_full_fields.avsc";
    private static final String READER_10_SCHEMA_RESOURCE = "avro/reader_consumer1_10_fields.avsc";

    private static volatile long SINK = 0L;

    private AvroReaderSchemaMain() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.load();
        Schema fullSchema = loadSchema(FULL_SCHEMA_RESOURCE);
        Schema reader10Schema = loadSchema(READER_10_SCHEMA_RESOURCE);

        RoleMetrics metrics;
        if (config.role == Role.PRODUCER) {
            metrics = runMeasured(config, () -> runProducer(config, fullSchema));
        } else if (config.role == Role.CONSUMER_1) {
            metrics = runMeasured(config, () -> runConsumer1(config, fullSchema, reader10Schema));
        } else {
            metrics = runMeasured(config, () -> runConsumer2(config, fullSchema));
        }

        writeMetricsFile(config, metrics);
        System.out.println("METRICS " + metrics.toSingleLine());
    }

    private static RoleMetrics runMeasured(Config config, ProcessingRole processingRole) throws Exception {
        OperatingSystemMXBean osBean = getOperatingSystemMxBean();
        CpuSampler sampler = new CpuSampler(osBean);

        long startEpochMs = System.currentTimeMillis();
        long wallStartNs = System.nanoTime();
        long cpuStartNs = Math.max(0L, osBean.getProcessCpuTime());
        sampler.start();

        ProcessResult result = processingRole.process();

        long wallEndNs = System.nanoTime();
        long cpuEndNs = Math.max(0L, osBean.getProcessCpuTime());
        long endEpochMs = System.currentTimeMillis();

        long wallTimeMs = nanosToMillis(wallEndNs - wallStartNs);
        long processCpuTimeMs = nanosToMillis(cpuEndNs - cpuStartNs);
        CpuSnapshot cpuSnapshot = sampler.stop(processCpuTimeMs, wallTimeMs);

        double throughput = wallTimeMs > 0
                ? (result.recordsProcessed * 1000.0) / wallTimeMs
                : 0.0;

        double avgPayloadBytes = result.recordsProcessed > 0
                ? result.payloadStats.totalPayloadBytes / (double) result.recordsProcessed
                : 0.0;

        return new RoleMetrics(
                SCENARIO_NAME,
                config.role.value,
                startEpochMs,
                endEpochMs,
                result.recordsProcessed,
                processCpuTimeMs,
                cpuSnapshot.avgProcessCpuPct,
                cpuSnapshot.maxProcessCpuPct,
                wallTimeMs,
                throughput,
                result.payloadStats.totalPayloadBytes,
                avgPayloadBytes,
                result.payloadStats.minPayloadBytes,
                result.payloadStats.maxPayloadBytes,
                result.phaseStats.encodeOrBuildMs,
                result.phaseStats.decodeOrSplitMs,
                result.phaseStats.parseSelectedOrFullMs,
                result.phaseStats.roleTotalLoopMs,
                result.objectStats.objectCreateCount,
                result.objectStats.objectEncodeCount,
                result.objectStats.objectDecodeCount,
                result.objectStats.objectSkipCount
        );
    }

    private static ProcessResult runProducer(Config config, Schema fullSchema) throws Exception {
        waitForKafka(config.bootstrapServers);
        ensureTopicExists(config.bootstrapServers, config.topicName);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(fullSchema);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        BinaryEncoder encoder = null;

        long sent = 0L;
        long phaseEncodeOrBuildNs = 0L;
        long roleLoopStartNs = System.nanoTime();
        PayloadAccumulator payloadAccumulator = new PayloadAccumulator();
        ObjectStatsAccumulator objectStats = new ObjectStatsAccumulator();

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(producerProps)) {
            for (long sequence = 1L; sequence <= config.recordCount; sequence++) {
                long encodeStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                GenericRecord record = buildFullRecord(fullSchema, sequence);
                out.reset();
                encoder = EncoderFactory.get().binaryEncoder(out, encoder);
                writer.write(record, encoder);
                encoder.flush();
                if (config.phaseMetricsEnabled) {
                    phaseEncodeOrBuildNs += System.nanoTime() - encodeStartNs;
                }

                byte[] payload = out.toByteArray();
                payloadAccumulator.add(payload.length);
                objectStats.objectCreateCount++;
                objectStats.objectEncodeCount++;

                ProducerRecord<String, byte[]> kafkaRecord = new ProducerRecord<>(
                        config.topicName,
                        Long.toString(sequence),
                        payload
                );
                producer.send(kafkaRecord).get();
                sent++;
            }
            producer.flush();
        }

        long roleTotalLoopMs = nanosToMillis(System.nanoTime() - roleLoopStartNs);
        return new ProcessResult(
                sent,
                payloadAccumulator.snapshot(),
                new PhaseStatsLite(
                        nanosToMillis(phaseEncodeOrBuildNs),
                        0L,
                        0L,
                        roleTotalLoopMs
                ),
                objectStats.snapshot()
        );
    }

    private static ProcessResult runConsumer1(Config config, Schema fullSchema, Schema reader10Schema) throws Exception {
        waitForKafka(config.bootstrapServers);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, config.role.groupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(fullSchema, reader10Schema);
        BinaryDecoder decoder = null;

        long processed = 0L;
        long localSink = 0L;
        long phaseDecodeOrSplitNs = 0L;
        long phaseParseNs = 0L;
        long roleLoopStartNs = System.nanoTime();

        PayloadAccumulator payloadAccumulator = new PayloadAccumulator();
        ObjectStatsAccumulator objectStats = new ObjectStatsAccumulator();

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(config.topicName));

            while (processed < config.recordCount) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs));
                for (var record : records) {
                    byte[] payload = record.value();
                    if (payload == null) {
                        continue;
                    }

                    payloadAccumulator.add(payload.length);

                    long decodeStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    decoder = DecoderFactory.get().binaryDecoder(payload, decoder);
                    GenericRecord decoded = reader.read(null, decoder);
                    if (config.phaseMetricsEnabled) {
                        phaseDecodeOrSplitNs += System.nanoTime() - decodeStartNs;
                    }

                    long parseStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    localSink += sinkSelectedRecord(decoded, reader10Schema);
                    if (config.phaseMetricsEnabled) {
                        phaseParseNs += System.nanoTime() - parseStartNs;
                    }

                    objectStats.objectCreateCount++;
                    objectStats.objectDecodeCount++;

                    processed++;
                    if (processed >= config.recordCount) {
                        break;
                    }
                }
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }

            consumer.commitSync();
        }

        SINK ^= localSink;
        long roleTotalLoopMs = nanosToMillis(System.nanoTime() - roleLoopStartNs);
        return new ProcessResult(
                processed,
                payloadAccumulator.snapshot(),
                new PhaseStatsLite(
                        0L,
                        nanosToMillis(phaseDecodeOrSplitNs),
                        nanosToMillis(phaseParseNs),
                        roleTotalLoopMs
                ),
                objectStats.snapshot()
        );
    }

    private static ProcessResult runConsumer2(Config config, Schema fullSchema) throws Exception {
        waitForKafka(config.bootstrapServers);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, config.role.groupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(fullSchema, fullSchema);
        BinaryDecoder decoder = null;

        long processed = 0L;
        long localSink = 0L;
        long phaseDecodeOrSplitNs = 0L;
        long phaseParseNs = 0L;
        long roleLoopStartNs = System.nanoTime();

        PayloadAccumulator payloadAccumulator = new PayloadAccumulator();
        ObjectStatsAccumulator objectStats = new ObjectStatsAccumulator();

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(config.topicName));

            while (processed < config.recordCount) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs));
                for (var record : records) {
                    byte[] payload = record.value();
                    if (payload == null) {
                        continue;
                    }

                    payloadAccumulator.add(payload.length);

                    long decodeStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    decoder = DecoderFactory.get().binaryDecoder(payload, decoder);
                    GenericRecord decoded = reader.read(null, decoder);
                    if (config.phaseMetricsEnabled) {
                        phaseDecodeOrSplitNs += System.nanoTime() - decodeStartNs;
                    }

                    long parseStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    localSink += sinkFullRecord(decoded, fullSchema);
                    if (config.phaseMetricsEnabled) {
                        phaseParseNs += System.nanoTime() - parseStartNs;
                    }

                    objectStats.objectCreateCount++;
                    objectStats.objectDecodeCount++;

                    processed++;
                    if (processed >= config.recordCount) {
                        break;
                    }
                }
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }

            consumer.commitSync();
        }

        SINK ^= localSink;
        long roleTotalLoopMs = nanosToMillis(System.nanoTime() - roleLoopStartNs);
        return new ProcessResult(
                processed,
                payloadAccumulator.snapshot(),
                new PhaseStatsLite(
                        0L,
                        nanosToMillis(phaseDecodeOrSplitNs),
                        nanosToMillis(phaseParseNs),
                        roleTotalLoopMs
                ),
                objectStats.snapshot()
        );
    }

    private static GenericRecord buildFullRecord(Schema fullSchema, long sequence) {
        GenericRecord record = new GenericData.Record(fullSchema);
        for (int i = 0; i < StaticMappedObject.INT_FIELDS; i++) {
            record.put("int_" + i, StaticMappedObject.getInt(i));
        }

        for (int i = 0; i < StaticMappedObject.LONG_FIELDS; i++) {
            record.put("long_" + i, StaticMappedObject.getLong(i, sequence));
        }

        for (int i = 0; i < StaticMappedObject.STRING_FIELDS; i++) {
            record.put("str_" + i, StaticMappedObject.getString(i));
        }

        return record;
    }

    private static long sinkSelectedRecord(GenericRecord record, Schema readerSchema) {
        long value = 0L;
        for (Schema.Field field : readerSchema.getFields()) {
            value += sinkValue(record.get(field.name()), field.schema().getType());
        }
        return value;
    }

    private static long sinkFullRecord(GenericRecord record, Schema fullSchema) {
        long value = 0L;
        for (Schema.Field field : fullSchema.getFields()) {
            value += sinkValue(record.get(field.name()), field.schema().getType());
        }
        return value;
    }

    private static long sinkValue(Object value, Schema.Type type) {
        if (value == null) {
            return 0L;
        }

        if (type == Schema.Type.INT) {
            return ((Number) value).intValue();
        }
        if (type == Schema.Type.LONG) {
            return ((Number) value).longValue();
        }
        return value.toString().length();
    }

    private static Schema loadSchema(String resourcePath) throws IOException {
        try (InputStream inputStream = AvroReaderSchemaMain.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Schema resource not found: " + resourcePath);
            }
            return new Schema.Parser().parse(inputStream);
        }
    }

    private static void waitForKafka(String bootstrapServers) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(120);

        while (true) {
            try {
                Properties props = new Properties();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                try (AdminClient adminClient = AdminClient.create(props)) {
                    adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
                    return;
                }
            } catch (Exception ex) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new IllegalStateException("Kafka is not reachable after waiting 120 seconds.", ex);
                }
                Thread.sleep(1000);
            }
        }
    }

    private static void ensureTopicExists(String bootstrapServers, String topicName) throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(props)) {
            try {
                adminClient.createTopics(Collections.singletonList(new NewTopic(topicName, 1, (short) 1)))
                        .all()
                        .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                if (!(ex.getCause() instanceof TopicExistsException)) {
                    throw ex;
                }
            }
        }
    }

    private static void writeMetricsFile(Config config, RoleMetrics metrics) throws IOException {
        Path dir = Path.of(config.metricsDir);
        Files.createDirectories(dir);

        Path file = dir.resolve(config.role.value.replace('-', '_') + "_metrics.kv");
        Files.writeString(file, metrics.toKeyValueBlock(), StandardCharsets.UTF_8);
    }

    private static OperatingSystemMXBean getOperatingSystemMxBean() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean osBean) {
            return osBean;
        }
        throw new IllegalStateException("OperatingSystemMXBean is not supported for process CPU metrics.");
    }

    private static long nanosToMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, nanos));
    }

    @FunctionalInterface
    private interface ProcessingRole {
        ProcessResult process() throws Exception;
    }

    private enum Role {
        PRODUCER("producer", null),
        CONSUMER_1("consumer-1", "kb-avro-c1"),
        CONSUMER_2("consumer-2", "kb-avro-c2");

        private final String value;
        private final String groupId;

        Role(String value, String groupId) {
            this.value = value;
            this.groupId = groupId;
        }

        private static Role from(String value) {
            for (Role role : values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Unknown APP_ROLE: " + value);
        }
    }

    private record Config(
            Role role,
            String bootstrapServers,
            String topicName,
            long recordCount,
            int pollTimeoutMs,
            String metricsDir,
            boolean phaseMetricsEnabled
    ) {
        private static Config load() throws IOException {
            Properties props = new Properties();
            try (InputStream input = AvroReaderSchemaMain.class.getClassLoader().getResourceAsStream("benchmark.properties")) {
                if (input != null) {
                    props.load(input);
                }
            }

            Role role = Role.from(requireValue(System.getenv("APP_ROLE"), "APP_ROLE"));
            String bootstrap = readValue(props, "BOOTSTRAP_SERVERS", "bootstrap.servers", "kafka:9092");
            String topic = readValue(props, "TOPIC_NAME", "benchmark.topic.name", DEFAULT_TOPIC);
            long recordCount = Long.parseLong(readValue(
                    props,
                    "RECORD_COUNT",
                    "benchmark.record.count",
                    Integer.toString(DEFAULT_RECORD_COUNT)
            ));
            int pollTimeoutMs = Integer.parseInt(readValue(
                    props,
                    "POLL_TIMEOUT_MS",
                    "benchmark.poll.timeout.ms",
                    Integer.toString(DEFAULT_POLL_TIMEOUT_MS)
            ));
            String metricsDir = readValue(props, "METRICS_DIR", "benchmark.metrics.dir", "logs");
            boolean phaseMetricsEnabled = Boolean.parseBoolean(readValue(
                    props,
                    "PHASE_METRICS_ENABLED",
                    "benchmark.phase.metrics.enabled",
                    "true"
            ));

            return new Config(role, bootstrap, topic, recordCount, pollTimeoutMs, metricsDir, phaseMetricsEnabled);
        }

        private static String readValue(Properties props, String envKey, String propKey, String defaultValue) {
            String env = System.getenv(envKey);
            if (env != null && !env.isBlank()) {
                return env;
            }

            String value = props.getProperty(propKey);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }

            return defaultValue;
        }

        private static String requireValue(String value, String key) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required environment variable: " + key);
            }
            return value.trim();
        }
    }

    private static final class PayloadAccumulator {
        private long totalBytes;
        private long minBytes = Long.MAX_VALUE;
        private long maxBytes = Long.MIN_VALUE;

        private void add(int payloadLength) {
            long length = Math.max(0, payloadLength);
            totalBytes += length;
            if (length < minBytes) {
                minBytes = length;
            }
            if (length > maxBytes) {
                maxBytes = length;
            }
        }

        private PayloadStats snapshot() {
            long min = minBytes == Long.MAX_VALUE ? 0L : minBytes;
            long max = maxBytes == Long.MIN_VALUE ? 0L : maxBytes;
            return new PayloadStats(totalBytes, min, max);
        }
    }

    private static final class ObjectStatsAccumulator {
        private long objectCreateCount;
        private long objectEncodeCount;
        private long objectDecodeCount;
        private long objectSkipCount;

        private ObjectStats snapshot() {
            return new ObjectStats(objectCreateCount, objectEncodeCount, objectDecodeCount, objectSkipCount);
        }
    }

    private record PayloadStats(
            long totalPayloadBytes,
            long minPayloadBytes,
            long maxPayloadBytes
    ) {
    }

    private record PhaseStatsLite(
            long encodeOrBuildMs,
            long decodeOrSplitMs,
            long parseSelectedOrFullMs,
            long roleTotalLoopMs
    ) {
    }

    private record ObjectStats(
            long objectCreateCount,
            long objectEncodeCount,
            long objectDecodeCount,
            long objectSkipCount
    ) {
    }

    private record ProcessResult(
            long recordsProcessed,
            PayloadStats payloadStats,
            PhaseStatsLite phaseStats,
            ObjectStats objectStats
    ) {
    }

    private static final class CpuSampler {
        private final OperatingSystemMXBean osBean;
        private final ScheduledExecutorService scheduler;
        private final Object lock = new Object();
        private long samples;
        private double sumPct;
        private double maxPct;

        private CpuSampler(OperatingSystemMXBean osBean) {
            this.osBean = osBean;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "cpu-sampler");
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }

        private void start() {
            scheduler.scheduleAtFixedRate(this::sample, 1, 1, TimeUnit.SECONDS);
        }

        private CpuSnapshot stop(long processCpuTimeMs, long wallTimeMs) {
            sample();
            scheduler.shutdownNow();

            synchronized (lock) {
                if (samples == 0) {
                    double fallback = wallTimeMs > 0 ? (processCpuTimeMs * 100.0) / wallTimeMs : 0.0;
                    return new CpuSnapshot(fallback, fallback);
                }

                double avg = sumPct / samples;
                return new CpuSnapshot(avg, maxPct);
            }
        }

        private void sample() {
            double load = osBean.getProcessCpuLoad();
            if (load < 0.0) {
                return;
            }

            double pct = load * 100.0;
            synchronized (lock) {
                samples++;
                sumPct += pct;
                if (pct > maxPct) {
                    maxPct = pct;
                }
            }
        }
    }

    private record CpuSnapshot(double avgProcessCpuPct, double maxProcessCpuPct) {
    }

    private record RoleMetrics(
            String scenario,
            String role,
            long startEpochMs,
            long endEpochMs,
            long recordsProcessed,
            long processCpuTimeMs,
            double avgProcessCpuPct,
            double maxProcessCpuPct,
            long wallTimeMs,
            double throughputRecPerSec,
            long totalPayloadBytes,
            double avgPayloadBytes,
            long minPayloadBytes,
            long maxPayloadBytes,
            long phaseEncodeOrBuildMs,
            long phaseDecodeOrSplitMs,
            long phaseParseSelectedOrFullMs,
            long phaseRoleTotalLoopMs,
            long objectCreateCount,
            long objectEncodeCount,
            long objectDecodeCount,
            long objectSkipCount
    ) {
        private String toSingleLine() {
            return String.format(
                    "scenario=%s role=%s records_processed=%d process_cpu_time_ms=%d avg_process_cpu_pct=%.4f max_process_cpu_pct=%.4f wall_time_ms=%d throughput_rec_per_sec=%.4f total_payload_bytes=%d avg_payload_bytes=%.4f min_payload_bytes=%d max_payload_bytes=%d phase_encode_or_build_ms=%d phase_decode_or_split_ms=%d phase_parse_selected_or_full_ms=%d phase_role_total_loop_ms=%d object_create_count=%d object_encode_count=%d object_decode_count=%d object_skip_count=%d start_epoch_ms=%d end_epoch_ms=%d",
                    scenario,
                    role,
                    recordsProcessed,
                    processCpuTimeMs,
                    avgProcessCpuPct,
                    maxProcessCpuPct,
                    wallTimeMs,
                    throughputRecPerSec,
                    totalPayloadBytes,
                    avgPayloadBytes,
                    minPayloadBytes,
                    maxPayloadBytes,
                    phaseEncodeOrBuildMs,
                    phaseDecodeOrSplitMs,
                    phaseParseSelectedOrFullMs,
                    phaseRoleTotalLoopMs,
                    objectCreateCount,
                    objectEncodeCount,
                    objectDecodeCount,
                    objectSkipCount,
                    startEpochMs,
                    endEpochMs
            );
        }

        private String toKeyValueBlock() {
            return String.join("\n",
                    "scenario=" + scenario,
                    "role=" + role,
                    "start_epoch_ms=" + startEpochMs,
                    "end_epoch_ms=" + endEpochMs,
                    "records_processed=" + recordsProcessed,
                    "process_cpu_time_ms=" + processCpuTimeMs,
                    "avg_process_cpu_pct=" + String.format("%.4f", avgProcessCpuPct),
                    "max_process_cpu_pct=" + String.format("%.4f", maxProcessCpuPct),
                    "wall_time_ms=" + wallTimeMs,
                    "throughput_rec_per_sec=" + String.format("%.4f", throughputRecPerSec),
                    "total_payload_bytes=" + totalPayloadBytes,
                    "avg_payload_bytes=" + String.format("%.4f", avgPayloadBytes),
                    "min_payload_bytes=" + minPayloadBytes,
                    "max_payload_bytes=" + maxPayloadBytes,
                    "phase_encode_or_build_ms=" + phaseEncodeOrBuildMs,
                    "phase_decode_or_split_ms=" + phaseDecodeOrSplitMs,
                    "phase_parse_selected_or_full_ms=" + phaseParseSelectedOrFullMs,
                    "phase_role_total_loop_ms=" + phaseRoleTotalLoopMs,
                    "object_create_count=" + objectCreateCount,
                    "object_encode_count=" + objectEncodeCount,
                    "object_decode_count=" + objectDecodeCount,
                    "object_skip_count=" + objectSkipCount,
                    ""
            );
        }
    }
}
