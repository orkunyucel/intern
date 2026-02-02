package com.benchmark.generator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import static com.benchmark.generator.DataGenerator.*;

/**
 * Generates Avro (.avsc) and Protobuf (.proto) schema files matching the benchmark configuration.
 */
public class SchemaGenerator {

    /**
     * Generates the Avro schema file (schema.avsc)
     */
    public static void generateAvroSchema(String outputPath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"type\": \"record\",\n");
        json.append("  \"name\": \"TestRecord\",\n");
        json.append("  \"namespace\": \"com.benchmark.model\",\n");
        json.append("  \"fields\": [\n");

        boolean first = true;

        // int32 fields
        for (int i = 0; i < INT_FIELDS; i++) {
            if (!first) json.append(",\n");
            json.append(String.format("    {\"name\": \"int_%d\", \"type\": \"int\"}", i));
            first = false;
        }

        // int64 fields
        for (int i = 0; i < LONG_FIELDS; i++) {
            json.append(",\n");
            json.append(String.format("    {\"name\": \"long_%d\", \"type\": \"long\"}", i));
        }

        // string fields
        for (int i = 0; i < STRING_FIELDS; i++) {
            json.append(",\n");
            json.append(String.format("    {\"name\": \"str_%d\", \"type\": \"string\"}", i));
        }

        json.append("\n  ]\n");
        json.append("}\n");

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.print(json.toString());
        }

        System.out.printf("Generated %s with %d fields.%n", outputPath, TOTAL_FIELDS);
    }

    /**
     * Generates the Protobuf schema file (message.proto)
     */
    public static void generateProtoSchema(String outputPath) throws IOException {
        StringBuilder proto = new StringBuilder();
        proto.append("syntax = \"proto3\";\n\n");
        proto.append("package com.benchmark.model;\n\n");
        proto.append("option java_package = \"com.benchmark.model\";\n");
        proto.append("option java_outer_classname = \"TestMessageProto\";\n\n");
        proto.append("message TestMessage {\n");

        int fieldId = 1;

        // int32 fields
        for (int i = 0; i < INT_FIELDS; i++) {
            proto.append(String.format("  int32 int_%d = %d;%n", i, fieldId++));
        }

        // int64 fields
        for (int i = 0; i < LONG_FIELDS; i++) {
            proto.append(String.format("  int64 long_%d = %d;%n", i, fieldId++));
        }

        // string fields
        for (int i = 0; i < STRING_FIELDS; i++) {
            proto.append(String.format("  string str_%d = %d;%n", i, fieldId++));
        }

        proto.append("}\n");

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.print(proto.toString());
        }

        System.out.printf("Generated %s with %d fields.%n", outputPath, fieldId - 1);
    }

    public static void main(String[] args) throws IOException {
        String basePath = args.length > 0 ? args[0] : ".";

        generateAvroSchema(basePath + "/src/main/avro/TestRecord.avsc");
        generateProtoSchema(basePath + "/src/main/proto/TestMessage.proto");

        System.out.println("\nSchemas generated successfully!");
        System.out.println("Run 'mvn generate-sources' to compile the schemas.");
    }
}
