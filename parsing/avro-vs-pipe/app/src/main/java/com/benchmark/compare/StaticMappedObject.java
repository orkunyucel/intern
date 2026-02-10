package com.benchmark.compare;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StaticMappedObject {

    public static final int INT_FIELDS = 84;
    public static final int LONG_FIELDS = 83;
    public static final int STRING_FIELDS = 83;
    public static final int TOTAL_FIELDS = INT_FIELDS + LONG_FIELDS + STRING_FIELDS;

    private static final int[] CONSUMER1_SELECTED_POSITIONS = {
            5, 13, 19, 25, 93, 130, 134, 168, 220, 248
    };

    // Hardcoded Map - prod'daki gibi .get("field_name") ile erişim
    private static final Map<String, Object> DATA;

    static {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>(TOTAL_FIELDS);
        m.put("int_0", 100000001);
        m.put("int_1", 100000002);
        m.put("int_2", 100000003);
        m.put("int_3", 100000004);
        m.put("int_4", 100000005);
        m.put("int_5", 100000006);
        m.put("int_6", 100000007);
        m.put("int_7", 100000008);
        m.put("int_8", 100000009);
        m.put("int_9", 100000010);
        m.put("int_10", 100000011);
        m.put("int_11", 100000012);
        m.put("int_12", 100000013);
        m.put("int_13", 100000014);
        m.put("int_14", 100000015);
        m.put("int_15", 100000016);
        m.put("int_16", 100000017);
        m.put("int_17", 100000018);
        m.put("int_18", 100000019);
        m.put("int_19", 100000020);
        m.put("int_20", 100000021);
        m.put("int_21", 100000022);
        m.put("int_22", 100000023);
        m.put("int_23", 100000024);
        m.put("int_24", 100000025);
        m.put("int_25", 100000026);
        m.put("int_26", 100000027);
        m.put("int_27", 100000028);
        m.put("int_28", 100000029);
        m.put("int_29", 100000030);
        m.put("int_30", 100000031);
        m.put("int_31", 100000032);
        m.put("int_32", 100000033);
        m.put("int_33", 100000034);
        m.put("int_34", 100000035);
        m.put("int_35", 100000036);
        m.put("int_36", 100000037);
        m.put("int_37", 100000038);
        m.put("int_38", 100000039);
        m.put("int_39", 100000040);
        m.put("int_40", 100000041);
        m.put("int_41", 100000042);
        m.put("int_42", 100000043);
        m.put("int_43", 100000044);
        m.put("int_44", 100000045);
        m.put("int_45", 100000046);
        m.put("int_46", 100000047);
        m.put("int_47", 100000048);
        m.put("int_48", 100000049);
        m.put("int_49", 100000050);
        m.put("int_50", 100000051);
        m.put("int_51", 100000052);
        m.put("int_52", 100000053);
        m.put("int_53", 100000054);
        m.put("int_54", 100000055);
        m.put("int_55", 100000056);
        m.put("int_56", 100000057);
        m.put("int_57", 100000058);
        m.put("int_58", 100000059);
        m.put("int_59", 100000060);
        m.put("int_60", 100000061);
        m.put("int_61", 100000062);
        m.put("int_62", 100000063);
        m.put("int_63", 100000064);
        m.put("int_64", 100000065);
        m.put("int_65", 100000066);
        m.put("int_66", 100000067);
        m.put("int_67", 100000068);
        m.put("int_68", 100000069);
        m.put("int_69", 100000070);
        m.put("int_70", 100000071);
        m.put("int_71", 100000072);
        m.put("int_72", 100000073);
        m.put("int_73", 100000074);
        m.put("int_74", 100000075);
        m.put("int_75", 100000076);
        m.put("int_76", 100000077);
        m.put("int_77", 100000078);
        m.put("int_78", 100000079);
        m.put("int_79", 100000080);
        m.put("int_80", 100000081);
        m.put("int_81", 100000082);
        m.put("int_82", 100000083);
        m.put("int_83", 100000084);
        m.put("long_0", 100000000000001L);
        m.put("long_1", 100000000000002L);
        m.put("long_2", 100000000000003L);
        m.put("long_3", 100000000000004L);
        m.put("long_4", 100000000000005L);
        m.put("long_5", 100000000000006L);
        m.put("long_6", 100000000000007L);
        m.put("long_7", 100000000000008L);
        m.put("long_8", 100000000000009L);
        m.put("long_9", 100000000000010L);
        m.put("long_10", 100000000000011L);
        m.put("long_11", 100000000000012L);
        m.put("long_12", 100000000000013L);
        m.put("long_13", 100000000000014L);
        m.put("long_14", 100000000000015L);
        m.put("long_15", 100000000000016L);
        m.put("long_16", 100000000000017L);
        m.put("long_17", 100000000000018L);
        m.put("long_18", 100000000000019L);
        m.put("long_19", 100000000000020L);
        m.put("long_20", 100000000000021L);
        m.put("long_21", 100000000000022L);
        m.put("long_22", 100000000000023L);
        m.put("long_23", 100000000000024L);
        m.put("long_24", 100000000000025L);
        m.put("long_25", 100000000000026L);
        m.put("long_26", 100000000000027L);
        m.put("long_27", 100000000000028L);
        m.put("long_28", 100000000000029L);
        m.put("long_29", 100000000000030L);
        m.put("long_30", 100000000000031L);
        m.put("long_31", 100000000000032L);
        m.put("long_32", 100000000000033L);
        m.put("long_33", 100000000000034L);
        m.put("long_34", 100000000000035L);
        m.put("long_35", 100000000000036L);
        m.put("long_36", 100000000000037L);
        m.put("long_37", 100000000000038L);
        m.put("long_38", 100000000000039L);
        m.put("long_39", 100000000000040L);
        m.put("long_40", 100000000000041L);
        m.put("long_41", 100000000000042L);
        m.put("long_42", 100000000000043L);
        m.put("long_43", 100000000000044L);
        m.put("long_44", 100000000000045L);
        m.put("long_45", 100000000000046L);
        m.put("long_46", 100000000000047L);
        m.put("long_47", 100000000000048L);
        m.put("long_48", 100000000000049L);
        m.put("long_49", 100000000000050L);
        m.put("long_50", 100000000000051L);
        m.put("long_51", 100000000000052L);
        m.put("long_52", 100000000000053L);
        m.put("long_53", 100000000000054L);
        m.put("long_54", 100000000000055L);
        m.put("long_55", 100000000000056L);
        m.put("long_56", 100000000000057L);
        m.put("long_57", 100000000000058L);
        m.put("long_58", 100000000000059L);
        m.put("long_59", 100000000000060L);
        m.put("long_60", 100000000000061L);
        m.put("long_61", 100000000000062L);
        m.put("long_62", 100000000000063L);
        m.put("long_63", 100000000000064L);
        m.put("long_64", 100000000000065L);
        m.put("long_65", 100000000000066L);
        m.put("long_66", 100000000000067L);
        m.put("long_67", 100000000000068L);
        m.put("long_68", 100000000000069L);
        m.put("long_69", 100000000000070L);
        m.put("long_70", 100000000000071L);
        m.put("long_71", 100000000000072L);
        m.put("long_72", 100000000000073L);
        m.put("long_73", 100000000000074L);
        m.put("long_74", 100000000000075L);
        m.put("long_75", 100000000000076L);
        m.put("long_76", 100000000000077L);
        m.put("long_77", 100000000000078L);
        m.put("long_78", 100000000000079L);
        m.put("long_79", 100000000000080L);
        m.put("long_80", 100000000000081L);
        m.put("long_81", 100000000000082L);
        m.put("long_82", 100000000000083L);
        m.put("str_0", "STR_000");
        m.put("str_1", "STR_001");
        m.put("str_2", "STR_002");
        m.put("str_3", "STR_003");
        m.put("str_4", "STR_004");
        m.put("str_5", "STR_005");
        m.put("str_6", "STR_006");
        m.put("str_7", "STR_007");
        m.put("str_8", "STR_008");
        m.put("str_9", "STR_009");
        m.put("str_10", "STR_010");
        m.put("str_11", "STR_011");
        m.put("str_12", "STR_012");
        m.put("str_13", "STR_013");
        m.put("str_14", "STR_014");
        m.put("str_15", "STR_015");
        m.put("str_16", "STR_016");
        m.put("str_17", "STR_017");
        m.put("str_18", "STR_018");
        m.put("str_19", "STR_019");
        m.put("str_20", "STR_020");
        m.put("str_21", "STR_021");
        m.put("str_22", "STR_022");
        m.put("str_23", "STR_023");
        m.put("str_24", "STR_024");
        m.put("str_25", "STR_025");
        m.put("str_26", "STR_026");
        m.put("str_27", "STR_027");
        m.put("str_28", "STR_028");
        m.put("str_29", "STR_029");
        m.put("str_30", "STR_030");
        m.put("str_31", "STR_031");
        m.put("str_32", "STR_032");
        m.put("str_33", "STR_033");
        m.put("str_34", "STR_034");
        m.put("str_35", "STR_035");
        m.put("str_36", "STR_036");
        m.put("str_37", "STR_037");
        m.put("str_38", "STR_038");
        m.put("str_39", "STR_039");
        m.put("str_40", "STR_040");
        m.put("str_41", "STR_041");
        m.put("str_42", "STR_042");
        m.put("str_43", "STR_043");
        m.put("str_44", "STR_044");
        m.put("str_45", "STR_045");
        m.put("str_46", "STR_046");
        m.put("str_47", "STR_047");
        m.put("str_48", "STR_048");
        m.put("str_49", "STR_049");
        m.put("str_50", "STR_050");
        m.put("str_51", "STR_051");
        m.put("str_52", "STR_052");
        m.put("str_53", "STR_053");
        m.put("str_54", "STR_054");
        m.put("str_55", "STR_055");
        m.put("str_56", "STR_056");
        m.put("str_57", "STR_057");
        m.put("str_58", "STR_058");
        m.put("str_59", "STR_059");
        m.put("str_60", "STR_060");
        m.put("str_61", "STR_061");
        m.put("str_62", "STR_062");
        m.put("str_63", "STR_063");
        m.put("str_64", "STR_064");
        m.put("str_65", "STR_065");
        m.put("str_66", "STR_066");
        m.put("str_67", "STR_067");
        m.put("str_68", "STR_068");
        m.put("str_69", "STR_069");
        m.put("str_70", "STR_070");
        m.put("str_71", "STR_071");
        m.put("str_72", "STR_072");
        m.put("str_73", "STR_073");
        m.put("str_74", "STR_074");
        m.put("str_75", "STR_075");
        m.put("str_76", "STR_076");
        m.put("str_77", "STR_077");
        m.put("str_78", "STR_078");
        m.put("str_79", "STR_079");
        m.put("str_80", "STR_080");
        m.put("str_81", "STR_081");
        m.put("str_82", "STR_082");
        DATA = Collections.unmodifiableMap(m);
    }

    // Sıralı alan adları (pipe record için sıra gerekli)
    private static final String[] FIELD_NAMES;
    static {
        FIELD_NAMES = DATA.keySet().toArray(new String[0]);
    }

    private StaticMappedObject() {
    }

    /**
     * Prod'daki gibi: .get("int_5") → O(1) HashMap lookup
     */
    public static Object get(String fieldName) {
        return DATA.get(fieldName);
    }

    /**
     * Tüm Map'i döndür (read-only)
     */
    public static Map<String, Object> getData() {
        return DATA;
    }

    /**
     * Pipe format: tüm alanları | ile birleştir
     */
    public static String toPipeRecord(long sequence) {
        StringBuilder sb = new StringBuilder(4096);
        boolean first = true;
        for (Map.Entry<String, Object> entry : DATA.entrySet()) {
            if (!first)
                sb.append('|');
            if ("long_0".equals(entry.getKey())) {
                sb.append(sequence);
            } else {
                sb.append(entry.getValue());
            }
            first = false;
        }
        return sb.toString();
    }

    public static int[] consumer1SelectedPositions() {
        return CONSUMER1_SELECTED_POSITIONS.clone();
    }

    /**
     * Consumer-1'in seçtiği 10 alanın adları
     */
    public static String[] consumer1SelectedFieldNames() {
        return new String[] {
                "int_5", "int_13", "int_19", "int_25",
                "long_9", "long_46", "long_50",
                "str_1", "str_53", "str_81"
        };
    }

    public static String fieldNameAt(int position) {
        return FIELD_NAMES[position];
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
