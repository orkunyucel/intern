package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for retrieving sessions by device as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrieveSessionsRequest {

    private Device device;

    public RetrieveSessionsRequest() {
    }

    public RetrieveSessionsRequest(Device device) {
        this.device = device;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
}
