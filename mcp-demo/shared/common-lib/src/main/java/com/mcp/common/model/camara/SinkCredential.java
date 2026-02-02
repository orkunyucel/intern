package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sink Credential for callback authentication as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SinkCredential {

    private String credentialType;
    private String accessToken;
    private String accessTokenExpiresUtc;
    private String accessTokenType;

    public SinkCredential() {
    }

    public static SinkCredential plain() {
        SinkCredential cred = new SinkCredential();
        cred.setCredentialType("PLAIN");
        return cred;
    }

    public static SinkCredential withAccessToken(String accessToken) {
        SinkCredential cred = new SinkCredential();
        cred.setCredentialType("ACCESSTOKEN");
        cred.setAccessToken(accessToken);
        cred.setAccessTokenType("Bearer");
        return cred;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessTokenExpiresUtc() {
        return accessTokenExpiresUtc;
    }

    public void setAccessTokenExpiresUtc(String accessTokenExpiresUtc) {
        this.accessTokenExpiresUtc = accessTokenExpiresUtc;
    }

    public String getAccessTokenType() {
        return accessTokenType;
    }

    public void setAccessTokenType(String accessTokenType) {
        this.accessTokenType = accessTokenType;
    }
}
