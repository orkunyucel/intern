package com.benchmark.runner.proto;

import com.benchmark.model.BenchmarkResult;
import com.benchmark.model.FieldSpec;
import com.benchmark.model.FieldType;
import com.benchmark.model.TestMessageProto.TestMessage;
import com.benchmark.runner.BenchmarkStrategy;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.benchmark.generator.DataGenerator.*;

/**
 * Protobuf serialization/deserialization benchmark.
 * Measures pure CPU time for encoding and decoding operations.
 */
public class ProtobufBenchmark implements BenchmarkStrategy {

    private final List<FieldSpec> selectedFields;
    private final Map<Integer, FieldType> selectedFieldTypes;
    private final Map<Integer, Integer> selectedFieldIndex;
    private final int selectedCount;
    private volatile long outputSink;

    public ProtobufBenchmark(List<FieldSpec> selectedFields) {
        this.selectedFields = selectedFields;
        this.selectedFieldTypes = selectedFields.stream()
                .collect(Collectors.toMap(FieldSpec::fieldNumber, FieldSpec::type));
        this.selectedFieldIndex = new java.util.HashMap<>(selectedFields.size());
        for (int i = 0; i < selectedFields.size(); i++) {
            FieldSpec field = selectedFields.get(i);
            this.selectedFieldIndex.put(field.fieldNumber(), i);
        }
        this.selectedCount = selectedFields.size();
    }

    @Override
    public String getName() {
        return "Protobuf";
    }

    @Override
    public BenchmarkResult measureSelected(List<String> records) throws IOException {
        long start = System.nanoTime();
        long totalBytes = 0;
        long outputChars = 0;

        for (String pipeRecord : records) {
            byte[] data = serialize(pipeRecord);
            totalBytes += data.length;
            String out = parseSelectedFields(data);
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
            TestMessage.parseFrom(data);
        }

        long end = System.nanoTime();
        double timeSeconds = (end - start) / 1_000_000_000.0;

        return new BenchmarkResult(timeSeconds, 0, totalBytes, records.size());
    }

    /**
     * Converts a pipe-separated string to a Protobuf TestMessage.
     * This mimics the "Ingestion" phase: Parse CSV/Pipe -> Convert to Protobuf.
     */
    private TestMessage pipeToProtoMessage(String pipeRecord) {
        TestMessage.Builder builder = TestMessage.newBuilder();
        String[] parts = pipeRecord.split("\\|", -1);
        int index = 0;

        // Set int32 fields
        builder.setInt0(Integer.parseInt(parts[index++]));
        builder.setInt1(Integer.parseInt(parts[index++]));
        builder.setInt2(Integer.parseInt(parts[index++]));
        builder.setInt3(Integer.parseInt(parts[index++]));
        builder.setInt4(Integer.parseInt(parts[index++]));
        builder.setInt5(Integer.parseInt(parts[index++]));
        builder.setInt6(Integer.parseInt(parts[index++]));
        builder.setInt7(Integer.parseInt(parts[index++]));
        builder.setInt8(Integer.parseInt(parts[index++]));
        builder.setInt9(Integer.parseInt(parts[index++]));
        builder.setInt10(Integer.parseInt(parts[index++]));
        builder.setInt11(Integer.parseInt(parts[index++]));
        builder.setInt12(Integer.parseInt(parts[index++]));
        builder.setInt13(Integer.parseInt(parts[index++]));
        builder.setInt14(Integer.parseInt(parts[index++]));
        builder.setInt15(Integer.parseInt(parts[index++]));
        builder.setInt16(Integer.parseInt(parts[index++]));
        builder.setInt17(Integer.parseInt(parts[index++]));
        builder.setInt18(Integer.parseInt(parts[index++]));
        builder.setInt19(Integer.parseInt(parts[index++]));
        builder.setInt20(Integer.parseInt(parts[index++]));
        builder.setInt21(Integer.parseInt(parts[index++]));
        builder.setInt22(Integer.parseInt(parts[index++]));
        builder.setInt23(Integer.parseInt(parts[index++]));
        builder.setInt24(Integer.parseInt(parts[index++]));
        builder.setInt25(Integer.parseInt(parts[index++]));
        builder.setInt26(Integer.parseInt(parts[index++]));
        builder.setInt27(Integer.parseInt(parts[index++]));
        builder.setInt28(Integer.parseInt(parts[index++]));
        builder.setInt29(Integer.parseInt(parts[index++]));
        builder.setInt30(Integer.parseInt(parts[index++]));
        builder.setInt31(Integer.parseInt(parts[index++]));
        builder.setInt32(Integer.parseInt(parts[index++]));
        builder.setInt33(Integer.parseInt(parts[index++]));
        builder.setInt34(Integer.parseInt(parts[index++]));
        builder.setInt35(Integer.parseInt(parts[index++]));
        builder.setInt36(Integer.parseInt(parts[index++]));
        builder.setInt37(Integer.parseInt(parts[index++]));
        builder.setInt38(Integer.parseInt(parts[index++]));
        builder.setInt39(Integer.parseInt(parts[index++]));
        builder.setInt40(Integer.parseInt(parts[index++]));
        builder.setInt41(Integer.parseInt(parts[index++]));
        builder.setInt42(Integer.parseInt(parts[index++]));
        builder.setInt43(Integer.parseInt(parts[index++]));
        builder.setInt44(Integer.parseInt(parts[index++]));
        builder.setInt45(Integer.parseInt(parts[index++]));
        builder.setInt46(Integer.parseInt(parts[index++]));
        builder.setInt47(Integer.parseInt(parts[index++]));
        builder.setInt48(Integer.parseInt(parts[index++]));
        builder.setInt49(Integer.parseInt(parts[index++]));
        builder.setInt50(Integer.parseInt(parts[index++]));
        builder.setInt51(Integer.parseInt(parts[index++]));
        builder.setInt52(Integer.parseInt(parts[index++]));
        builder.setInt53(Integer.parseInt(parts[index++]));
        builder.setInt54(Integer.parseInt(parts[index++]));
        builder.setInt55(Integer.parseInt(parts[index++]));
        builder.setInt56(Integer.parseInt(parts[index++]));
        builder.setInt57(Integer.parseInt(parts[index++]));
        builder.setInt58(Integer.parseInt(parts[index++]));
        builder.setInt59(Integer.parseInt(parts[index++]));
        builder.setInt60(Integer.parseInt(parts[index++]));
        builder.setInt61(Integer.parseInt(parts[index++]));
        builder.setInt62(Integer.parseInt(parts[index++]));
        builder.setInt63(Integer.parseInt(parts[index++]));
        builder.setInt64(Integer.parseInt(parts[index++]));
        builder.setInt65(Integer.parseInt(parts[index++]));
        builder.setInt66(Integer.parseInt(parts[index++]));
        builder.setInt67(Integer.parseInt(parts[index++]));
        builder.setInt68(Integer.parseInt(parts[index++]));
        builder.setInt69(Integer.parseInt(parts[index++]));
        builder.setInt70(Integer.parseInt(parts[index++]));
        builder.setInt71(Integer.parseInt(parts[index++]));
        builder.setInt72(Integer.parseInt(parts[index++]));
        builder.setInt73(Integer.parseInt(parts[index++]));
        builder.setInt74(Integer.parseInt(parts[index++]));
        builder.setInt75(Integer.parseInt(parts[index++]));
        builder.setInt76(Integer.parseInt(parts[index++]));
        builder.setInt77(Integer.parseInt(parts[index++]));
        builder.setInt78(Integer.parseInt(parts[index++]));
        builder.setInt79(Integer.parseInt(parts[index++]));
        builder.setInt80(Integer.parseInt(parts[index++]));
        builder.setInt81(Integer.parseInt(parts[index++]));
        builder.setInt82(Integer.parseInt(parts[index++]));
        builder.setInt83(Integer.parseInt(parts[index++]));

        // Set int64 (long) fields
        builder.setLong0(Long.parseLong(parts[index++]));
        builder.setLong1(Long.parseLong(parts[index++]));
        builder.setLong2(Long.parseLong(parts[index++]));
        builder.setLong3(Long.parseLong(parts[index++]));
        builder.setLong4(Long.parseLong(parts[index++]));
        builder.setLong5(Long.parseLong(parts[index++]));
        builder.setLong6(Long.parseLong(parts[index++]));
        builder.setLong7(Long.parseLong(parts[index++]));
        builder.setLong8(Long.parseLong(parts[index++]));
        builder.setLong9(Long.parseLong(parts[index++]));
        builder.setLong10(Long.parseLong(parts[index++]));
        builder.setLong11(Long.parseLong(parts[index++]));
        builder.setLong12(Long.parseLong(parts[index++]));
        builder.setLong13(Long.parseLong(parts[index++]));
        builder.setLong14(Long.parseLong(parts[index++]));
        builder.setLong15(Long.parseLong(parts[index++]));
        builder.setLong16(Long.parseLong(parts[index++]));
        builder.setLong17(Long.parseLong(parts[index++]));
        builder.setLong18(Long.parseLong(parts[index++]));
        builder.setLong19(Long.parseLong(parts[index++]));
        builder.setLong20(Long.parseLong(parts[index++]));
        builder.setLong21(Long.parseLong(parts[index++]));
        builder.setLong22(Long.parseLong(parts[index++]));
        builder.setLong23(Long.parseLong(parts[index++]));
        builder.setLong24(Long.parseLong(parts[index++]));
        builder.setLong25(Long.parseLong(parts[index++]));
        builder.setLong26(Long.parseLong(parts[index++]));
        builder.setLong27(Long.parseLong(parts[index++]));
        builder.setLong28(Long.parseLong(parts[index++]));
        builder.setLong29(Long.parseLong(parts[index++]));
        builder.setLong30(Long.parseLong(parts[index++]));
        builder.setLong31(Long.parseLong(parts[index++]));
        builder.setLong32(Long.parseLong(parts[index++]));
        builder.setLong33(Long.parseLong(parts[index++]));
        builder.setLong34(Long.parseLong(parts[index++]));
        builder.setLong35(Long.parseLong(parts[index++]));
        builder.setLong36(Long.parseLong(parts[index++]));
        builder.setLong37(Long.parseLong(parts[index++]));
        builder.setLong38(Long.parseLong(parts[index++]));
        builder.setLong39(Long.parseLong(parts[index++]));
        builder.setLong40(Long.parseLong(parts[index++]));
        builder.setLong41(Long.parseLong(parts[index++]));
        builder.setLong42(Long.parseLong(parts[index++]));
        builder.setLong43(Long.parseLong(parts[index++]));
        builder.setLong44(Long.parseLong(parts[index++]));
        builder.setLong45(Long.parseLong(parts[index++]));
        builder.setLong46(Long.parseLong(parts[index++]));
        builder.setLong47(Long.parseLong(parts[index++]));
        builder.setLong48(Long.parseLong(parts[index++]));
        builder.setLong49(Long.parseLong(parts[index++]));
        builder.setLong50(Long.parseLong(parts[index++]));
        builder.setLong51(Long.parseLong(parts[index++]));
        builder.setLong52(Long.parseLong(parts[index++]));
        builder.setLong53(Long.parseLong(parts[index++]));
        builder.setLong54(Long.parseLong(parts[index++]));
        builder.setLong55(Long.parseLong(parts[index++]));
        builder.setLong56(Long.parseLong(parts[index++]));
        builder.setLong57(Long.parseLong(parts[index++]));
        builder.setLong58(Long.parseLong(parts[index++]));
        builder.setLong59(Long.parseLong(parts[index++]));
        builder.setLong60(Long.parseLong(parts[index++]));
        builder.setLong61(Long.parseLong(parts[index++]));
        builder.setLong62(Long.parseLong(parts[index++]));
        builder.setLong63(Long.parseLong(parts[index++]));
        builder.setLong64(Long.parseLong(parts[index++]));
        builder.setLong65(Long.parseLong(parts[index++]));
        builder.setLong66(Long.parseLong(parts[index++]));
        builder.setLong67(Long.parseLong(parts[index++]));
        builder.setLong68(Long.parseLong(parts[index++]));
        builder.setLong69(Long.parseLong(parts[index++]));
        builder.setLong70(Long.parseLong(parts[index++]));
        builder.setLong71(Long.parseLong(parts[index++]));
        builder.setLong72(Long.parseLong(parts[index++]));
        builder.setLong73(Long.parseLong(parts[index++]));
        builder.setLong74(Long.parseLong(parts[index++]));
        builder.setLong75(Long.parseLong(parts[index++]));
        builder.setLong76(Long.parseLong(parts[index++]));
        builder.setLong77(Long.parseLong(parts[index++]));
        builder.setLong78(Long.parseLong(parts[index++]));
        builder.setLong79(Long.parseLong(parts[index++]));
        builder.setLong80(Long.parseLong(parts[index++]));
        builder.setLong81(Long.parseLong(parts[index++]));
        builder.setLong82(Long.parseLong(parts[index++]));

        // Set string fields
        // Efficient way to set sequential string fields from array
        builder.setStr0(parts[index++]);
        builder.setStr1(parts[index++]);
        builder.setStr2(parts[index++]);
        builder.setStr3(parts[index++]);
        builder.setStr4(parts[index++]);
        builder.setStr5(parts[index++]);
        builder.setStr6(parts[index++]);
        builder.setStr7(parts[index++]);
        builder.setStr8(parts[index++]);
        builder.setStr9(parts[index++]);
        builder.setStr10(parts[index++]);
        builder.setStr11(parts[index++]);
        builder.setStr12(parts[index++]);
        builder.setStr13(parts[index++]);
        builder.setStr14(parts[index++]);
        builder.setStr15(parts[index++]);
        builder.setStr16(parts[index++]);
        builder.setStr17(parts[index++]);
        builder.setStr18(parts[index++]);
        builder.setStr19(parts[index++]);
        builder.setStr20(parts[index++]);
        builder.setStr21(parts[index++]);
        builder.setStr22(parts[index++]);
        builder.setStr23(parts[index++]);
        builder.setStr24(parts[index++]);
        builder.setStr25(parts[index++]);
        builder.setStr26(parts[index++]);
        builder.setStr27(parts[index++]);
        builder.setStr28(parts[index++]);
        builder.setStr29(parts[index++]);
        builder.setStr30(parts[index++]);
        builder.setStr31(parts[index++]);
        builder.setStr32(parts[index++]);
        builder.setStr33(parts[index++]);
        builder.setStr34(parts[index++]);
        builder.setStr35(parts[index++]);
        builder.setStr36(parts[index++]);
        builder.setStr37(parts[index++]);
        builder.setStr38(parts[index++]);
        builder.setStr39(parts[index++]);
        builder.setStr40(parts[index++]);
        builder.setStr41(parts[index++]);
        builder.setStr42(parts[index++]);
        builder.setStr43(parts[index++]);
        builder.setStr44(parts[index++]);
        builder.setStr45(parts[index++]);
        builder.setStr46(parts[index++]);
        builder.setStr47(parts[index++]);
        builder.setStr48(parts[index++]);
        builder.setStr49(parts[index++]);
        builder.setStr50(parts[index++]);
        builder.setStr51(parts[index++]);
        builder.setStr52(parts[index++]);
        builder.setStr53(parts[index++]);
        builder.setStr54(parts[index++]);
        builder.setStr55(parts[index++]);
        builder.setStr56(parts[index++]);
        builder.setStr57(parts[index++]);
        builder.setStr58(parts[index++]);
        builder.setStr59(parts[index++]);
        builder.setStr60(parts[index++]);
        builder.setStr61(parts[index++]);
        builder.setStr62(parts[index++]);
        builder.setStr63(parts[index++]);
        builder.setStr64(parts[index++]);
        builder.setStr65(parts[index++]);
        builder.setStr66(parts[index++]);
        builder.setStr67(parts[index++]);
        builder.setStr68(parts[index++]);
        builder.setStr69(parts[index++]);
        builder.setStr70(parts[index++]);
        builder.setStr71(parts[index++]);
        builder.setStr72(parts[index++]);
        builder.setStr73(parts[index++]);
        builder.setStr74(parts[index++]);
        builder.setStr75(parts[index++]);
        builder.setStr76(parts[index++]);
        builder.setStr77(parts[index++]);
        builder.setStr78(parts[index++]);
        builder.setStr79(parts[index++]);
        builder.setStr80(parts[index++]);
        builder.setStr81(parts[index++]);
        builder.setStr82(parts[index++]);

        return builder.build();
    }

    /**
     * Parses only selected fields from a protobuf binary payload.
     */
    private String parseSelectedFields(byte[] data) throws IOException {
        CodedInputStream input = CodedInputStream.newInstance(data);
        String[] values = new String[selectedCount];
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            FieldType type = selectedFieldTypes.get(fieldNumber);
            if (type == null) {
                input.skipField(tag);
                continue;
            }

            Integer index = selectedFieldIndex.get(fieldNumber);
            if (type == FieldType.INT) {
                int val = input.readInt32();
                if (index != null) {
                    values[index] = Integer.toString(val);
                }
            } else if (type == FieldType.LONG) {
                long val = input.readInt64();
                if (index != null) {
                    values[index] = Long.toString(val);
                }
            } else {
                String val = input.readStringRequireUtf8();
                if (index != null) {
                    values[index] = val;
                }
            }
        }

        StringBuilder sb = new StringBuilder(256);
        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append('|');
            }
            if (value != null) {
                sb.append(value);
            }
            first = false;
        }
        return sb.toString();
    }

    private byte[] serialize(String pipeRecord) {
        TestMessage message = pipeToProtoMessage(pipeRecord);
        return message.toByteArray();
    }

    /**
     * Standalone benchmark runner for Protobuf
     */
    public static void main(String[] args) throws IOException {
        int recordCount = args.length > 0 ? Integer.parseInt(args[0]) : 5000;

        ProtobufBenchmark benchmark = new ProtobufBenchmark(List.of());
        com.benchmark.generator.DataGenerator generator = new com.benchmark.generator.DataGenerator();

        System.out.printf("Generating %d records...%n", recordCount);
        List<String> records = new java.util.ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            records.add(generator.generatePipeRecord());
        }

        System.out.println("Running Protobuf benchmark (full pipeline)...");
        BenchmarkResult result = benchmark.measureFull(records);

        System.out.println("\n=== PROTOBUF RESULTS ===");
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
