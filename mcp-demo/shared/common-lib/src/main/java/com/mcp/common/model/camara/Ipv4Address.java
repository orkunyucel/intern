package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * IPv4 Address model as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Ipv4Address {

    private String publicAddress;
    private String privateAddress;
    private Integer publicPort;

    public Ipv4Address() {
    }

    public Ipv4Address(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    public Ipv4Address(String publicAddress, Integer publicPort) {
        this.publicAddress = publicAddress;
        this.publicPort = publicPort;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    public String getPrivateAddress() {
        return privateAddress;
    }

    public void setPrivateAddress(String privateAddress) {
        this.privateAddress = privateAddress;
    }

    public Integer getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(Integer publicPort) {
        this.publicPort = publicPort;
    }
}
