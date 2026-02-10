package com.benchmark.compare;

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
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class AvroVsPipeMain {

    private static final String FULL_SCHEMA_RESOURCE = "avro/full_record.avsc";
    private static final String READER_10_SCHEMA_RESOURCE = "avro/reader_10_fields.avsc";

    private static final int DEFAULT_RECORD_COUNT = 1_000_000;
    private static final int DEFAULT_POLL_TIMEOUT_MS = 200;

    private static volatile long SINK = 0L;

    private AvroVsPipeMain() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.load();

        Schema fullSchema = null;
        Schema reader10Schema = null;
        if (config.appCase == AppCase.AVRO) {
            fullSchema = loadSchema(FULL_SCHEMA_RESOURCE);
            reader10Schema = loadSchema(READER_10_SCHEMA_RESOURCE);
        }

        RoleMetrics metrics;
        if (config.role == Role.PRODUCER) {
            if (config.appCase == AppCase.PIPE) {
                metrics = runMeasured(config, AvroVsPipeMain::runPipeProducer);
            } else {
                Schema finalFullSchema = fullSchema;
                metrics = runMeasured(config, cfg -> runAvroProducer(cfg, finalFullSchema));
            }
        } else {
            if (config.appCase == AppCase.PIPE) {
                metrics = runMeasured(config, AvroVsPipeMain::runPipeConsumer1);
            } else {
                Schema finalFullSchema = fullSchema;
                Schema finalReader10Schema = reader10Schema;
                metrics = runMeasured(config, cfg -> runAvroConsumer1(cfg, finalFullSchema, finalReader10Schema));
            }
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

        ProcessResult result = processingRole.process(config);

        long wallEndNs = System.nanoTime();
        long cpuEndNs = Math.max(0L, osBean.getProcessCpuTime());
        long endEpochMs = System.currentTimeMillis();

        long roleWallTimeMs = nanosToMillis(wallEndNs - wallStartNs);
        long processCpuTimeMs = nanosToMillis(cpuEndNs - cpuStartNs);
        CpuSnapshot cpuSnapshot = sampler.stop(processCpuTimeMs, roleWallTimeMs);

        double throughput = roleWallTimeMs > 0
                ? (result.recordsProcessed * 1000.0) / roleWallTimeMs
                : 0.0;

        long arrowMapToProducerEncodeMs = nanosToMillis(result.arrowMapToProducerEncodeNs);
        long arrowProducerToTopicMs = nanosToMillis(result.arrowProducerToTopicNs);
        long arrowTopicToConsumerDecodeOrSplitMs = nanosToMillis(result.arrowTopicToConsumerDecodeOrSplitNs);
        long arrowTopicToConsumerParseSelectedMs = nanosToMillis(result.arrowTopicToConsumerParseSelectedNs);
        long arrowTopicToConsumerTotalMs = arrowTopicToConsumerDecodeOrSplitMs + arrowTopicToConsumerParseSelectedMs;

        return new RoleMetrics(
                config.appCase.value,
                config.role.value,
                startEpochMs,
                endEpochMs,
                result.recordsProcessed,
                processCpuTimeMs,
                cpuSnapshot.avgProcessCpuPct,
                cpuSnapshot.maxProcessCpuPct,
                roleWallTimeMs,
                throughput,
                arrowMapToProducerEncodeMs,
                arrowProducerToTopicMs,
                arrowTopicToConsumerDecodeOrSplitMs,
                arrowTopicToConsumerParseSelectedMs,
                arrowTopicToConsumerTotalMs);
    }

    private static ProcessResult runPipeProducer(Config config) throws Exception {
        waitForKafka(config.bootstrapServers);
        ensureTopicExists(config.bootstrapServers, config.topicName);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        long sent = 0L;
        long encodeNs = 0L;
        long sendNs = 0L;

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (long sequence = 1L; sequence <= config.recordCount; sequence++) {
                long encodeStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                String payload = StaticMappedObject.toPipeRecord(sequence);
                if (config.phaseMetricsEnabled) {
                    encodeNs += System.nanoTime() - encodeStartNs;
                }

                ProducerRecord<String, String> record = new ProducerRecord<>(config.topicName, Long.toString(sequence),
                        payload);

                long sendStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                producer.send(record).get();
                if (config.phaseMetricsEnabled) {
                    sendNs += System.nanoTime() - sendStartNs;
                }

                sent++;
            }
            producer.flush();
        }

        return new ProcessResult(sent, encodeNs, sendNs, 0L, 0L);
    }

    private static ProcessResult runAvroProducer(Config config, Schema fullSchema) throws Exception {
        waitForKafka(config.bootstrapServers);
        ensureTopicExists(config.bootstrapServers, config.topicName);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "0");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(fullSchema);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        BinaryEncoder encoder = null;
        GenericRecord reusableRecord = buildFullRecordTemplate(fullSchema);

        long sent = 0L;
        long encodeNs = 0L;
        long sendNs = 0L;

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            for (long sequence = 1L; sequence <= config.recordCount; sequence++) {
                long encodeStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;

                reusableRecord.put("long_0", sequence);
                out.reset();
                encoder = EncoderFactory.get().binaryEncoder(out, encoder);
                writer.write(reusableRecord, encoder);
                encoder.flush();
                byte[] payload = out.toByteArray();

                if (config.phaseMetricsEnabled) {
                    encodeNs += System.nanoTime() - encodeStartNs;
                }

                ProducerRecord<String, byte[]> kafkaRecord = new ProducerRecord<>(
                        config.topicName,
                        Long.toString(sequence),
                        payload);

                long sendStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                producer.send(kafkaRecord).get();
                if (config.phaseMetricsEnabled) {
                    sendNs += System.nanoTime() - sendStartNs;
                }

                sent++;
            }
            producer.flush();
        }

        return new ProcessResult(sent, encodeNs, sendNs, 0L, 0L);
    }

    private static ProcessResult runPipeConsumer1(Config config) throws Exception {
        waitForKafka(config.bootstrapServers);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "avp-pipe-c1");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        long processed = 0L;
        long localSink = 0L;
        long splitNs = 0L;
        long parseNs = 0L;

        int[] selectedPositions = StaticMappedObject.consumer1SelectedPositions();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(config.topicName));

            while (processed < config.recordCount) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs));
                for (ConsumerRecord<String, String> record : records) {
                    String payload = record.value();
                    if (payload == null) {
                        continue;
                    }

                    long splitStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    String[] parts = payload.split("\\|", -1);
                    if (config.phaseMetricsEnabled) {
                        splitNs += System.nanoTime() - splitStartNs;
                    }

                    if (parts.length >= StaticMappedObject.TOTAL_FIELDS) {
                        long parseStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;

                        int[] ints = new int[StaticMappedObject.INT_FIELDS];
                        long[] longs = new long[StaticMappedObject.LONG_FIELDS];
                        int[] strLens = new int[StaticMappedObject.STRING_FIELDS];

                        for (int i = 0; i < StaticMappedObject.INT_FIELDS; i++) {
                            ints[i] = Integer.parseInt(parts[i]);
                        }

                        int longStart = StaticMappedObject.INT_FIELDS;
                        int longEnd = longStart + StaticMappedObject.LONG_FIELDS;
                        for (int i = longStart; i < longEnd; i++) {
                            longs[i - longStart] = Long.parseLong(parts[i]);
                        }

                        for (int i = longEnd; i < StaticMappedObject.TOTAL_FIELDS; i++) {
                            strLens[i - longEnd] = parts[i].length();
                        }

                        long value = 0L;
                        for (int position : selectedPositions) {
                            if (position < StaticMappedObject.INT_FIELDS) {
                                value += ints[position];
                            } else if (position < StaticMappedObject.INT_FIELDS + StaticMappedObject.LONG_FIELDS) {
                                value += longs[position - StaticMappedObject.INT_FIELDS];
                            } else {
                                value += strLens[position - StaticMappedObject.INT_FIELDS
                                        - StaticMappedObject.LONG_FIELDS];
                            }
                        }
                        localSink += value;

                        if (config.phaseMetricsEnabled) {
                            parseNs += System.nanoTime() - parseStartNs;
                        }
                    }

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
        return new ProcessResult(processed, 0L, 0L, splitNs, parseNs);
    }

    private static ProcessResult runAvroConsumer1(Config config, Schema fullSchema, Schema reader10Schema)
            throws Exception {
        waitForKafka(config.bootstrapServers);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "avp-avro-c1");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(fullSchema, reader10Schema);
        BinaryDecoder decoder = null;

        long processed = 0L;
        long localSink = 0L;
        long decodeNs = 0L;
        long parseNs = 0L;

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(config.topicName));

            while (processed < config.recordCount) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(config.pollTimeoutMs));
                for (ConsumerRecord<String, byte[]> record : records) {
                    byte[] payload = record.value();
                    if (payload == null) {
                        continue;
                    }

                    long decodeStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    decoder = DecoderFactory.get().binaryDecoder(payload, decoder);
                    GenericRecord decoded = reader.read(null, decoder);
                    if (config.phaseMetricsEnabled) {
                        decodeNs += System.nanoTime() - decodeStartNs;
                    }

                    long parseStartNs = config.phaseMetricsEnabled ? System.nanoTime() : 0L;
                    localSink += sinkReader10Record(decoded, reader10Schema);
                    if (config.phaseMetricsEnabled) {
                        parseNs += System.nanoTime() - parseStartNs;
                    }

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
        return new ProcessResult(processed, 0L, 0L, decodeNs, parseNs);
    }

    private static GenericRecord buildFullRecordTemplate(Schema fullSchema) {
        GenericRecord record = new GenericData.Record(fullSchema);
        Map<String, Object> data = StaticMappedObject.getData();
        for (Schema.Field field : fullSchema.getFields()) {
            String fieldName = field.name();
            record.put(fieldName, data.get(fieldName));
        }
        return record;
    }

    private static long sinkReader10Record(GenericRecord record, Schema readerSchema) {
        long value = 0L;
        for (Schema.Field field : readerSchema.getFields()) {
            Object cell = record.get(field.name());
            Schema.Type type = field.schema().getType();
            if (cell == null) {
                continue;
            }
            if (type == Schema.Type.INT) {
                value += ((Number) cell).intValue();
            } else if (type == Schema.Type.LONG) {
                value += ((Number) cell).longValue();
            } else {
                value += cell.toString().length();
            }
        }
        return value;
    }

    private static Schema loadSchema(String resourcePath) throws IOException {
        try (InputStream inputStream = AvroVsPipeMain.class.getClassLoader().getResourceAsStream(resourcePath)) {
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

        String fileName = config.appCase.value + "_" + config.role.value.replace('-', '_') + "_metrics.kv";
        Path file = dir.resolve(fileName);
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
        ProcessResult process(Config config) throws Exception;
    }

    private enum AppCase {
        PIPE("pipe", "RAW_PIPE"),
        AVRO("avro", "AVRO_FULL");

        private final String value;
        private final String defaultTopic;

        AppCase(String value, String defaultTopic) {
            this.value = value;
            this.defaultTopic = defaultTopic;
        }

        private static AppCase from(String value) {
            for (AppCase appCase : values()) {
                if (appCase.value.equals(value)) {
                    return appCase;
                }
            }
            throw new IllegalArgumentException("Unknown APP_CASE: " + value);
        }
    }

    private enum Role {
        PRODUCER("producer"),
        CONSUMER_1("consumer-1");

        private final String value;

        Role(String value) {
            this.value = value;
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
            AppCase appCase,
            Role role,
            String bootstrapServers,
            String topicName,
            long recordCount,
            int pollTimeoutMs,
            String metricsDir,
            boolean phaseMetricsEnabled) {
        private static Config load() throws IOException {
            Properties props = new Properties();
            try (InputStream input = AvroVsPipeMain.class.getClassLoader()
                    .getResourceAsStream("benchmark.properties")) {
                if (input != null) {
                    props.load(input);
                }
            }

            AppCase appCase = AppCase.from(requireValue(System.getenv("APP_CASE"), "APP_CASE"));
            Role role = Role.from(requireValue(System.getenv("APP_ROLE"), "APP_ROLE"));

            String bootstrap = readValue(props, "BOOTSTRAP_SERVERS", "bootstrap.servers", "kafka:9092");
            String topic = readValue(props, "TOPIC_NAME", "benchmark.topic.name", appCase.defaultTopic);
            long recordCount = Long.parseLong(readValue(
                    props,
                    "RECORD_COUNT",
                    "benchmark.record.count",
                    Integer.toString(DEFAULT_RECORD_COUNT)));
            int pollTimeoutMs = Integer.parseInt(readValue(
                    props,
                    "POLL_TIMEOUT_MS",
                    "benchmark.poll.timeout.ms",
                    Integer.toString(DEFAULT_POLL_TIMEOUT_MS)));
            String metricsDir = readValue(props, "METRICS_DIR", "benchmark.metrics.dir", "logs");
            boolean phaseMetricsEnabled = Boolean.parseBoolean(readValue(
                    props,
                    "PHASE_METRICS_ENABLED",
                    "benchmark.phase.metrics.enabled",
                    "true"));

            return new Config(appCase, role, bootstrap, topic, recordCount, pollTimeoutMs, metricsDir,
                    phaseMetricsEnabled);
        }

        private static String readValue(Properties props, String envKey, String propKey, String defaultValue) {
            String env = System.getenv(envKey);
            if (env != null && !env.isBlank()) {
                return env.trim();
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

    private record ProcessResult(
            long recordsProcessed,
            long arrowMapToProducerEncodeNs,
            long arrowProducerToTopicNs,
            long arrowTopicToConsumerDecodeOrSplitNs,
            long arrowTopicToConsumerParseSelectedNs) {
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
                return new CpuSnapshot(sumPct / samples, maxPct);
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
            String appCase,
            String role,
            long startEpochMs,
            long endEpochMs,
            long recordsProcessed,
            long processCpuTimeMs,
            double avgProcessCpuPct,
            double maxProcessCpuPct,
            long roleWallTimeMs,
            double throughputRecPerSec,
            long arrowMapToProducerEncodeMs,
            long arrowProducerToTopicMs,
            long arrowTopicToConsumerDecodeOrSplitMs,
            long arrowTopicToConsumerParseSelectedMs,
            long arrowTopicToConsumerTotalMs) {
        private String toSingleLine() {
            return String.format(
                    "case=%s role=%s records_processed=%d process_cpu_time_ms=%d avg_process_cpu_pct=%.4f max_process_cpu_pct=%.4f role_wall_time_ms=%d throughput_rec_per_sec=%.4f arrow_map_to_producer_encode_ms=%d arrow_producer_to_topic_ms=%d arrow_topic_to_consumer_decode_or_split_ms=%d arrow_topic_to_consumer_parse_selected_ms=%d arrow_topic_to_consumer_total_ms=%d start_epoch_ms=%d end_epoch_ms=%d",
                    appCase,
                    role,
                    recordsProcessed,
                    processCpuTimeMs,
                    avgProcessCpuPct,
                    maxProcessCpuPct,
                    roleWallTimeMs,
                    throughputRecPerSec,
                    arrowMapToProducerEncodeMs,
                    arrowProducerToTopicMs,
                    arrowTopicToConsumerDecodeOrSplitMs,
                    arrowTopicToConsumerParseSelectedMs,
                    arrowTopicToConsumerTotalMs,
                    startEpochMs,
                    endEpochMs);
        }

        private String toKeyValueBlock() {
            return String.join("\n",
                    "case=" + appCase,
                    "role=" + role,
                    "start_epoch_ms=" + startEpochMs,
                    "end_epoch_ms=" + endEpochMs,
                    "records_processed=" + recordsProcessed,
                    "process_cpu_time_ms=" + processCpuTimeMs,
                    "avg_process_cpu_pct=" + String.format("%.4f", avgProcessCpuPct),
                    "max_process_cpu_pct=" + String.format("%.4f", maxProcessCpuPct),
                    "role_wall_time_ms=" + roleWallTimeMs,
                    "wall_time_ms=" + roleWallTimeMs,
                    "throughput_rec_per_sec=" + String.format("%.4f", throughputRecPerSec),
                    "arrow_map_to_producer_encode_ms=" + arrowMapToProducerEncodeMs,
                    "arrow_producer_to_topic_ms=" + arrowProducerToTopicMs,
                    "arrow_topic_to_consumer_decode_or_split_ms=" + arrowTopicToConsumerDecodeOrSplitMs,
                    "arrow_topic_to_consumer_parse_selected_ms=" + arrowTopicToConsumerParseSelectedMs,
                    "arrow_topic_to_consumer_total_ms=" + arrowTopicToConsumerTotalMs,
                    "");
        }
    }
}
