package io.axoniq.cli.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * @author Marc Gathier
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class ClusterNode {
    private String name;

    private String hostName;
    private String internalHostName;
    private Integer grpcPort;
    private Integer internalGrpcPort;
    private Integer httpPort;
    private boolean connected;
    private boolean master;

    private List<String> contexts;

    public ClusterNode(String internalHostName, Integer internalGrpcPort) {
        this.internalHostName = internalHostName;
        this.internalGrpcPort = internalGrpcPort;
    }

    public ClusterNode() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getInternalHostName() {
        return internalHostName;
    }

    public void setInternalHostName(String internalHostName) {
        this.internalHostName = internalHostName;
    }

    public Integer getGrpcPort() {
        return grpcPort;
    }

    public void setGrpcPort(Integer grpcPort) {
        this.grpcPort = grpcPort;
    }

    public Integer getInternalGrpcPort() {
        return internalGrpcPort;
    }

    public void setInternalGrpcPort(Integer internalGrpcPort) {
        this.internalGrpcPort = internalGrpcPort;
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public void setContexts(List<String> contexts) {
        this.contexts = contexts;
    }
}
