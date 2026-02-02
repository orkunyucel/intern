package com.mcp.common.model.camara;

/**
 * QoS Profile types as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 *
 * These profiles define different levels of Quality of Service.
 * The actual meaning and parameters may vary by operator.
 */
public enum QosProfile {

    QOS_S("QOS_S", "Standard", "Basic QoS with minimal priority"),
    QOS_M("QOS_M", "Medium", "Medium priority with reduced latency"),
    QOS_L("QOS_L", "Large/High", "High priority with low latency guarantee"),
    QOS_E("QOS_E", "Extreme", "Maximum priority with best latency and throughput");

    private final String code;
    private final String displayName;
    private final String description;

    QosProfile(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static QosProfile fromCode(String code) {
        if (code == null) return null;

        for (QosProfile profile : values()) {
            if (profile.code.equalsIgnoreCase(code) || profile.name().equalsIgnoreCase(code)) {
                return profile;
            }
        }
        return null;
    }

    public static String getAllProfilesDescription() {
        StringBuilder sb = new StringBuilder();
        for (QosProfile profile : values()) {
            sb.append(String.format("  - %s: %s\n", profile.code, profile.description));
        }
        return sb.toString();
    }
}
