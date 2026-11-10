package com.example.reporting.spike;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * SIB connection settings for the Phase 0 spike ({@code reporting.sib.*}).
 *
 * <p>Throwaway: Phase 4 replaces this with the validated settings of
 * {@code SibConnectionFactoryConfig}. Values are not validated here because the class is bound in
 * every profile; {@link SibSpikeConfig} checks them when the spike actually runs.
 */
@ConfigurationProperties("reporting.sib")
@ConstructorBinding
public final class SibSpikeProperties {

    private static final String DEFAULT_PAYLOAD_BYTES = "26214400";

    private final String busName;
    private final String providerEndpoints;
    private final String targetTransportChain;
    private final String username;
    private final String password;
    private final int spikePayloadBytes;

    /**
     * Creates the properties; called by Spring Boot's binder.
     *
     * @param busName SIB bus name
     * @param providerEndpoints bootstrap endpoints, {@code host:port:chain[,...]}
     * @param targetTransportChain inbound transport chain of the messaging engine
     * @param username optional user for the connection
     * @param password optional password for the connection
     * @param spikePayloadBytes size of the spike message body (default 25 MB)
     */
    public SibSpikeProperties(
            String busName,
            String providerEndpoints,
            @DefaultValue("InboundBasicMessaging") String targetTransportChain,
            @DefaultValue("") String username,
            @DefaultValue("") String password,
            @DefaultValue(DEFAULT_PAYLOAD_BYTES) int spikePayloadBytes) {
        this.busName = busName;
        this.providerEndpoints = providerEndpoints;
        this.targetTransportChain = targetTransportChain;
        this.username = username;
        this.password = password;
        this.spikePayloadBytes = spikePayloadBytes;
    }

    public String getBusName() {
        return busName;
    }

    public String getProviderEndpoints() {
        return providerEndpoints;
    }

    public String getTargetTransportChain() {
        return targetTransportChain;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getSpikePayloadBytes() {
        return spikePayloadBytes;
    }
}
