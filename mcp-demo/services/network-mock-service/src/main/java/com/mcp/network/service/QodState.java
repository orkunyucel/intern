package com.mcp.network.service;

import org.springframework.stereotype.Component;

/**
 * QoD (Quality on Demand) State - In-memory bandwidth state
 *
 * LAYER: NETWORK INFRASTRUCTURE (Mock)
 *
 * This simulates the network state that can be modified by LLM actions.
 * In a real scenario, this would be the actual network configuration
 * managed by the telco operator's 5G/LTE core network.
 */
@Component
public class QodState {

    private int currentBandwidthMbps = 500;  // Default starting value
    private static final int MIN_BANDWIDTH = 100;
    private static final int MAX_BANDWIDTH = 1000;

    public synchronized int getBandwidth() {
        return currentBandwidthMbps;
    }

    public synchronized int setBandwidth(int mbps) {
        int oldValue = currentBandwidthMbps;
        // Clamp to valid range
        currentBandwidthMbps = Math.max(MIN_BANDWIDTH, Math.min(MAX_BANDWIDTH, mbps));
        return oldValue;
    }

    public int getMinBandwidth() {
        return MIN_BANDWIDTH;
    }

    public int getMaxBandwidth() {
        return MAX_BANDWIDTH;
    }

    public void reset() {
        currentBandwidthMbps = 500;
    }
}
