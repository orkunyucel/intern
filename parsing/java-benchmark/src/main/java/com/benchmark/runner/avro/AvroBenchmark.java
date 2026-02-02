package com.benchmark.runner.avro;

import com.benchmark.generator.DataGenerator;
import com.benchmark.model.BenchmarkResult;
import com.benchmark.model.FieldSpec;
import com.benchmark.runner.BenchmarkStrategy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.benchmark.generator.DataGenerator.*;

/**
 * Avro serialization/deserialization benchmark.
 * Measures pure CPU time for encoding and decoding operations.
 */
public class AvroBenchmark implements BenchmarkStrategy {

    private final Schema schema;
    private final Schema selectedSchema;
    private final List<FieldSpec> selectedFields;
    private final GenericDatumWriter<GenericRecord> writer;
    private final DatumReader<GenericRecord> fullReader;
    private final DatumReader<GenericRecord> selectedReader;
    private volatile long outputSink;

    public AvroBenchmark(Schema schema, List<FieldSpec> selectedFields) {
        this.schema = schema;
        this.selectedSchema = buildSelectedSchema(schema, selectedFields);
        this.selectedFields = selectedFields;
        this.writer = new GenericDatumWriter<>(schema);
        this.fullReader = new GenericDatumReader<>(schema);
        this.selectedReader = new GenericDatumReader<>(schema, selectedSchema);
    }

    @Override
    public String getName() {
        return "Avro";
    }

    @Override
    public BenchmarkResult measureSelected(List<String> records) throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        long outputChars = 0;

        for (String pipeRecord : records) {
            byte[] data = serialize(pipeRecord);
            totalBytes += data.length;
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
            GenericRecord record = selectedReader.read(null, decoder);
            String out = buildSelectedPipe(record);
            outputChars += out.length();
        }

        long end = System.nanoTime();
        outputSink = outputChars;
        double timeSeconds = (end - start) / 1_000_000_000.0;

        return new BenchmarkResult(timeSeconds, 0, totalBytes, records.size());
    }

    @Override
    public BenchmarkResult measureFull(List<String> records) throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;

        for (String pipeRecord : records) {
            byte[] data = serialize(pipeRecord);
            totalBytes += data.length;
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
            fullReader.read(null, decoder);
        }

        long end = System.nanoTime();
        double timeSeconds = (end - start) / 1_000_000_000.0;

        return new BenchmarkResult(timeSeconds, 0, totalBytes, records.size());
    }

    /**
     * Converts a pipe-separated string to an Avro GenericRecord.
     * This mimics the "Ingestion" phase: Parse CSV/Pipe -> Convert to Avro.
     */
    private GenericRecord pipeToAvroRecord(String pipeRecord) {
        GenericRecord record = new GenericData.Record(schema);
        String[] parts = pipeRecord.split("\\|", -1);
        int index = 0;

        for (int i = 0; i < INT_FIELDS; i++) {
            record.put("int_" + i, Integer.parseInt(parts[index++]));
        }

        for (int i = 0; i < LONG_FIELDS; i++) {
            record.put("long_" + i, Long.parseLong(parts[index++]));
        }

        for (int i = 0; i < STRING_FIELDS; i++) {
            record.put("str_" + i, parts[index++]);
        }

        return record;
    }

    private byte[] serialize(String pipeRecord) throws IOException {
        GenericRecord record = pipeToAvroRecord(pipeRecord);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(record, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private String buildSelectedPipe(GenericRecord record) {
        StringBuilder sb = new StringBuilder(256);
        boolean first = true;
        for (FieldSpec field : selectedFields) {
            if (!first) {
                sb.append('|');
            }
            Object value = record.get(field.name());
            sb.append(value);
            first = false;
        }
        return sb.toString();
    }

    /**
     * Loads Avro schema from file
     */
    public static Schema loadSchema(String schemaPath) throws IOException {
        return new Schema.Parser().parse(new File(schemaPath));
    }

    private static Schema buildSelectedSchema(Schema fullSchema, List<FieldSpec> selectedFields) {
        Set<String> selectedNames = selectedFields.stream()
                .map(FieldSpec::name)
                .collect(Collectors.toSet());

        List<Schema.Field> selected = fullSchema.getFields().stream()
                .filter(field -> selectedNames.contains(field.name()))
                .map(field -> new Schema.Field(field.name(), field.schema(), field.doc(), field.defaultVal()))
                .collect(Collectors.toList());

        Schema schema = Schema.createRecord(fullSchema.getName(), fullSchema.getDoc(),
                fullSchema.getNamespace(), false);
        schema.setFields(selected);
        return schema;
    }

    /**
     * Standalone benchmark runner for Avro
     */
    public static void main(String[] args) throws IOException {
        String schemaPath = args.length > 0 ? args[0] : "src/main/avro/TestRecord.avsc";
        int recordCount = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        System.out.println("Loading Avro schema from: " + schemaPath);
        Schema schema = loadSchema(schemaPath);

        AvroBenchmark benchmark = new AvroBenchmark(schema, List.of());
        DataGenerator generator = new DataGenerator();

        System.out.printf("Generating %d records...%n", recordCount);
        List<String> records = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            records.add(generator.generatePipeRecord());
        }

        System.out.println("Running Avro benchmark (full pipeline)...");
        BenchmarkResult result = benchmark.measureFull(records);

        System.out.println("\n=== AVRO RESULTS ===");
        System.out.printf("Total records: %d%n", result.recordCount());
        System.out.printf("Serialization (Ingest) time: %.4f s%n", result.serializationTimeSeconds());
        System.out.printf("Deserialization time: %.4f s%n", result.deserializationTimeSeconds());
        System.out.printf("Total bytes: %d%n", result.totalBytes());
        System.out.printf("Avg bytes per record: %.2f%n", result.avgBytesPerRecord());
        System.out.printf("Ser throughput: %d rec/s%n", result.serializationThroughput());
        System.out.printf("Des throughput: %d rec/s%n", result.deserializationThroughput());
        System.out.printf("Ser latency: %.2f µs/rec%n", result.serializationLatencyMicros());
        System.out.printf("Des latency: %.2f µs/rec%n", result.deserializationLatencyMicros());
    }
}
