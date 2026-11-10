/**
 * Test-only stand-in for the IBM WebSphere SIB thin-client JMS API (WAS 9.0).
 *
 * <p>Only the calls that {@code SibSpikeConfig} makes reflectively are provided. The factories route to the
 * in-VM Artemis broker {@link MockSibBus}, so the SIB code path can run end to end on a laptop without
 * WebSphere. These classes live in test sources and are never packaged into the WAR; on the staging server
 * the real {@code com.ibm.ws.sib.client.thin.jms_9.0.0.jar} provides the same names.
 */
package com.ibm.websphere.sib.api.jms;
