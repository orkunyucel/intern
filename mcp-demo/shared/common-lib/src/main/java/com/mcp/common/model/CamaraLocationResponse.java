package com.mcp.common.model;

/**
 * CAMARA Device Location API Response
 *
 * LAYER: Common (Shared Model)
 */
public class CamaraLocationResponse {

    private String country;
    private String city;
    private boolean roaming;
    private boolean verified;

    public CamaraLocationResponse() {
    }

    public CamaraLocationResponse(String country, String city, boolean roaming, boolean verified) {
        this.country = country;
        this.city = city;
        this.roaming = roaming;
        this.verified = verified;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public boolean isRoaming() {
        return roaming;
    }

    public void setRoaming(boolean roaming) {
        this.roaming = roaming;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
