package com.benchmark.model;

public record FieldSpec(
        FieldType type,
        int index,
        int position,
        int fieldNumber,
        String name) {
}
