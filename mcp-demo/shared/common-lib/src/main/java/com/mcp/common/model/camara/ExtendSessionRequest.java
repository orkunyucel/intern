package com.mcp.common.model.camara;

/**
 * Request body for extending a QoS session as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
public class ExtendSessionRequest {

    private Integer requestedAdditionalDuration;

    public ExtendSessionRequest() {
    }

    public ExtendSessionRequest(Integer requestedAdditionalDuration) {
        this.requestedAdditionalDuration = requestedAdditionalDuration;
    }

    public Integer getRequestedAdditionalDuration() {
        return requestedAdditionalDuration;
    }

    public void setRequestedAdditionalDuration(Integer requestedAdditionalDuration) {
        this.requestedAdditionalDuration = requestedAdditionalDuration;
    }
}
