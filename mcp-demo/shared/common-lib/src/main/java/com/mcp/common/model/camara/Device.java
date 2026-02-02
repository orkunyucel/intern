package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Device model as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Device {

    private String phoneNumber;
    private Ipv4Address ipv4Address;
    private String ipv6Address;
    private String networkAccessIdentifier;

    public Device() {
    }

    public Device(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public static Device withPhoneNumber(String phoneNumber) {
        Device device = new Device();
        device.setPhoneNumber(phoneNumber);
        return device;
    }

    public static Device withIpv4(String ipAddress) {
        Device device = new Device();
        device.setIpv4Address(new Ipv4Address(ipAddress));
        return device;
    }

    public static Device withIpv4(String ipAddress, int port) {
        Device device = new Device();
        device.setIpv4Address(new Ipv4Address(ipAddress, port));
        return device;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Ipv4Address getIpv4Address() {
        return ipv4Address;
    }

    public void setIpv4Address(Ipv4Address ipv4Address) {
        this.ipv4Address = ipv4Address;
    }

    public String getIpv6Address() {
        return ipv6Address;
    }

    public void setIpv6Address(String ipv6Address) {
        this.ipv6Address = ipv6Address;
    }

    public String getNetworkAccessIdentifier() {
        return networkAccessIdentifier;
    }

    public void setNetworkAccessIdentifier(String networkAccessIdentifier) {
        this.networkAccessIdentifier = networkAccessIdentifier;
    }
}
