package com.ibm.websphere.sib.api.jms;

import javax.jms.ConnectionFactory;

/** Test stand-in for the SIB connection factory: the setters {@code SibSpikeConfig} calls. */
public interface JmsConnectionFactory extends ConnectionFactory {

    /**
     * Sets the bus name.
     *
     * @param busName SIB bus name
     */
    void setBusName(String busName);

    /**
     * Sets the bootstrap endpoints.
     *
     * @param endpoints {@code host:port:chain[,...]}
     */
    void setProviderEndpoints(String endpoints);

    /**
     * Sets the inbound transport chain.
     *
     * @param chain chain name
     */
    void setTargetTransportChain(String chain);
}
