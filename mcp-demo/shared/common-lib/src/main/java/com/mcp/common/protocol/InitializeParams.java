package com.mcp.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters for initialize method
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitializeParams {

    @JsonProperty("protocolVersion")
    private String protocolVersion = "2025-11-25";

    @JsonProperty("capabilities")
    private Capabilities capabilities;

    @JsonProperty("clientInfo")
    private ClientInfo clientInfo;

    public InitializeParams() {
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(ClientInfo clientInfo) {
        this.clientInfo = clientInfo;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Capabilities {
        // Client capabilities - can be extended
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("version")
        private String version;

        public ClientInfo() {
        }

        public ClientInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
