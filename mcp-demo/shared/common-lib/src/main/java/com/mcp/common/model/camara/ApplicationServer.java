package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Application Server model as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationServer {

    private String ipv4Address;
    private String ipv6Address;

    public ApplicationServer() {
    }

    public ApplicationServer(String ipv4Address) {
        this.ipv4Address = ipv4Address;
    }

    public static ApplicationServer withIpv4(String ipv4Address) {
        ApplicationServer server = new ApplicationServer();
        server.setIpv4Address(ipv4Address);
        return server;
    }

    public static ApplicationServer allTraffic() {
        return new ApplicationServer("0.0.0.0/0");
    }

    public String getIpv4Address() {
        return ipv4Address;
    }

    public void setIpv4Address(String ipv4Address) {
        this.ipv4Address = ipv4Address;
    }

    public String getIpv6Address() {
        return ipv6Address;
    }

    public void setIpv6Address(String ipv6Address) {
        this.ipv6Address = ipv6Address;
    }
}
