package com.mcp.common.model.camara;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Port specification as per CAMARA QoD API v1.1.0
 *
 * LAYER: CAMARA API (Model)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortsSpec {

    private List<Integer> ports;
    private List<PortRange> ranges;

    public PortsSpec() {
    }

    public static PortsSpec withPorts(List<Integer> ports) {
        PortsSpec spec = new PortsSpec();
        spec.setPorts(ports);
        return spec;
    }

    public static PortsSpec withRange(int from, int to) {
        PortsSpec spec = new PortsSpec();
        spec.setRanges(List.of(new PortRange(from, to)));
        return spec;
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public void setPorts(List<Integer> ports) {
        this.ports = ports;
    }

    public List<PortRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<PortRange> ranges) {
        this.ranges = ranges;
    }

    public static class PortRange {
        private Integer from;
        private Integer to;

        public PortRange() {
        }

        public PortRange(Integer from, Integer to) {
            this.from = from;
            this.to = to;
        }

        public Integer getFrom() {
            return from;
        }

        public void setFrom(Integer from) {
            this.from = from;
        }

        public Integer getTo() {
            return to;
        }

        public void setTo(Integer to) {
            this.to = to;
        }
    }
}
