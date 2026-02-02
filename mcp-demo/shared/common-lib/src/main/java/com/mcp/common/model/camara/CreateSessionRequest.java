package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for creating a QoS session as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateSessionRequest {

    private Device device;
    private ApplicationServer applicationServer;
    private PortsSpec devicePorts;
    private PortsSpec applicationServerPorts;
    private String qosProfile;
    private String sink;
    private SinkCredential sinkCredential;
    private Integer duration;

    public CreateSessionRequest() {
    }

    public CreateSessionRequest device(Device device) {
        this.device = device;
        return this;
    }

    public CreateSessionRequest applicationServer(ApplicationServer applicationServer) {
        this.applicationServer = applicationServer;
        return this;
    }

    public CreateSessionRequest qosProfile(String qosProfile) {
        this.qosProfile = qosProfile;
        return this;
    }

    public CreateSessionRequest qosProfile(QosProfile profile) {
        this.qosProfile = profile.getCode();
        return this;
    }

    public CreateSessionRequest duration(Integer duration) {
        this.duration = duration;
        return this;
    }

    public CreateSessionRequest sink(String sink) {
        this.sink = sink;
        return this;
    }

    public CreateSessionRequest sinkCredential(SinkCredential sinkCredential) {
        this.sinkCredential = sinkCredential;
        return this;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public ApplicationServer getApplicationServer() {
        return applicationServer;
    }

    public void setApplicationServer(ApplicationServer applicationServer) {
        this.applicationServer = applicationServer;
    }

    public PortsSpec getDevicePorts() {
        return devicePorts;
    }

    public void setDevicePorts(PortsSpec devicePorts) {
        this.devicePorts = devicePorts;
    }

    public PortsSpec getApplicationServerPorts() {
        return applicationServerPorts;
    }

    public void setApplicationServerPorts(PortsSpec applicationServerPorts) {
        this.applicationServerPorts = applicationServerPorts;
    }

    public String getQosProfile() {
        return qosProfile;
    }

    public void setQosProfile(String qosProfile) {
        this.qosProfile = qosProfile;
    }

    public String getSink() {
        return sink;
    }

    public void setSink(String sink) {
        this.sink = sink;
    }

    public SinkCredential getSinkCredential() {
        return sinkCredential;
    }

    public void setSinkCredential(SinkCredential sinkCredential) {
        this.sinkCredential = sinkCredential;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
