package com.benchmark.shared;

public final class StaticMappedObject {

    public static final int INT_FIELDS = 84;
    public static final int LONG_FIELDS = 83;
    public static final int STRING_FIELDS = 83;
    public static final int TOTAL_FIELDS = INT_FIELDS + LONG_FIELDS + STRING_FIELDS;

    private static final int[] CONSUMER1_SELECTED_POSITIONS = {
            5, 13, 19, 25, 93, 130, 134, 168, 220, 248
    };

    private static final int[] INT_VALUES = {
            100000001,
            100000002,
            100000003,
            100000004,
            100000005,
            100000006,
            100000007,
            100000008,
            100000009,
            100000010,
            100000011,
            100000012,
            100000013,
            100000014,
            100000015,
            100000016,
            100000017,
            100000018,
            100000019,
            100000020,
            100000021,
            100000022,
            100000023,
            100000024,
            100000025,
            100000026,
            100000027,
            100000028,
            100000029,
            100000030,
            100000031,
            100000032,
            100000033,
            100000034,
            100000035,
            100000036,
            100000037,
            100000038,
            100000039,
            100000040,
            100000041,
            100000042,
            100000043,
            100000044,
            100000045,
            100000046,
            100000047,
            100000048,
            100000049,
            100000050,
            100000051,
            100000052,
            100000053,
            100000054,
            100000055,
            100000056,
            100000057,
            100000058,
            100000059,
            100000060,
            100000061,
            100000062,
            100000063,
            100000064,
            100000065,
            100000066,
            100000067,
            100000068,
            100000069,
            100000070,
            100000071,
            100000072,
            100000073,
            100000074,
            100000075,
            100000076,
            100000077,
            100000078,
            100000079,
            100000080,
            100000081,
            100000082,
            100000083,
            100000084
    };

    private static final long[] LONG_VALUES = {
            100000000000001L,
            100000000000002L,
            100000000000003L,
            100000000000004L,
            100000000000005L,
            100000000000006L,
            100000000000007L,
            100000000000008L,
            100000000000009L,
            100000000000010L,
            100000000000011L,
            100000000000012L,
            100000000000013L,
            100000000000014L,
            100000000000015L,
            100000000000016L,
            100000000000017L,
            100000000000018L,
            100000000000019L,
            100000000000020L,
            100000000000021L,
            100000000000022L,
            100000000000023L,
            100000000000024L,
            100000000000025L,
            100000000000026L,
            100000000000027L,
            100000000000028L,
            100000000000029L,
            100000000000030L,
            100000000000031L,
            100000000000032L,
            100000000000033L,
            100000000000034L,
            100000000000035L,
            100000000000036L,
            100000000000037L,
            100000000000038L,
            100000000000039L,
            100000000000040L,
            100000000000041L,
            100000000000042L,
            100000000000043L,
            100000000000044L,
            100000000000045L,
            100000000000046L,
            100000000000047L,
            100000000000048L,
            100000000000049L,
            100000000000050L,
            100000000000051L,
            100000000000052L,
            100000000000053L,
            100000000000054L,
            100000000000055L,
            100000000000056L,
            100000000000057L,
            100000000000058L,
            100000000000059L,
            100000000000060L,
            100000000000061L,
            100000000000062L,
            100000000000063L,
            100000000000064L,
            100000000000065L,
            100000000000066L,
            100000000000067L,
            100000000000068L,
            100000000000069L,
            100000000000070L,
            100000000000071L,
            100000000000072L,
            100000000000073L,
            100000000000074L,
            100000000000075L,
            100000000000076L,
            100000000000077L,
            100000000000078L,
            100000000000079L,
            100000000000080L,
            100000000000081L,
            100000000000082L,
            100000000000083L
    };

    private static final String[] STRING_VALUES = {
            "STR_000",
            "STR_001",
            "STR_002",
            "STR_003",
            "STR_004",
            "STR_005",
            "STR_006",
            "STR_007",
            "STR_008",
            "STR_009",
            "STR_010",
            "STR_011",
            "STR_012",
            "STR_013",
            "STR_014",
            "STR_015",
            "STR_016",
            "STR_017",
            "STR_018",
            "STR_019",
            "STR_020",
            "STR_021",
            "STR_022",
            "STR_023",
            "STR_024",
            "STR_025",
            "STR_026",
            "STR_027",
            "STR_028",
            "STR_029",
            "STR_030",
            "STR_031",
            "STR_032",
            "STR_033",
            "STR_034",
            "STR_035",
            "STR_036",
            "STR_037",
            "STR_038",
            "STR_039",
            "STR_040",
            "STR_041",
            "STR_042",
            "STR_043",
            "STR_044",
            "STR_045",
            "STR_046",
            "STR_047",
            "STR_048",
            "STR_049",
            "STR_050",
            "STR_051",
            "STR_052",
            "STR_053",
            "STR_054",
            "STR_055",
            "STR_056",
            "STR_057",
            "STR_058",
            "STR_059",
            "STR_060",
            "STR_061",
            "STR_062",
            "STR_063",
            "STR_064",
            "STR_065",
            "STR_066",
            "STR_067",
            "STR_068",
            "STR_069",
            "STR_070",
            "STR_071",
            "STR_072",
            "STR_073",
            "STR_074",
            "STR_075",
            "STR_076",
            "STR_077",
            "STR_078",
            "STR_079",
            "STR_080",
            "STR_081",
            "STR_082"
    };

    private StaticMappedObject() {
    }

    public static String toPipeRecord(long sequence) {
        StringBuilder sb = new StringBuilder(4096);

        for (int i = 0; i < INT_FIELDS; i++) {
            sb.append(INT_VALUES[i]).append('|');
        }

        for (int i = 0; i < LONG_FIELDS; i++) {
            long value = (i == 0) ? sequence : LONG_VALUES[i];
            sb.append(value).append('|');
        }

        for (int i = 0; i < STRING_FIELDS; i++) {
            sb.append(STRING_VALUES[i]);
            if (i < STRING_FIELDS - 1) {
                sb.append('|');
            }
        }

        return sb.toString();
    }

    public static int[] consumer1SelectedPositions() {
        return CONSUMER1_SELECTED_POSITIONS.clone();
    }

    public static int getInt(int index) {
        if (index < 0 || index >= INT_FIELDS) {
            throw new IllegalArgumentException("Invalid int index: " + index);
        }
        return INT_VALUES[index];
    }

    public static long getLong(int index, long sequence) {
        if (index < 0 || index >= LONG_FIELDS) {
            throw new IllegalArgumentException("Invalid long index: " + index);
        }
        return index == 0 ? sequence : LONG_VALUES[index];
    }

    public static String getString(int index) {
        if (index < 0 || index >= STRING_FIELDS) {
            throw new IllegalArgumentException("Invalid string index: " + index);
        }
        return STRING_VALUES[index];
    }

    public static boolean isIntPosition(int position) {
        return position >= 0 && position < INT_FIELDS;
    }

    public static boolean isLongPosition(int position) {
        return position >= INT_FIELDS && position < INT_FIELDS + LONG_FIELDS;
    }

    public static boolean isStringPosition(int position) {
        return position >= INT_FIELDS + LONG_FIELDS && position < TOTAL_FIELDS;
    }
}
