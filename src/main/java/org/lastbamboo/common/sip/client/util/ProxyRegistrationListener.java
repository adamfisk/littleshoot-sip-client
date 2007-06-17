package org.lastbamboo.common.sip.client.util;

import java.net.URI;

/**
 * A callback interface for objects interested in knowing about registration
 * events.
 */
public interface ProxyRegistrationListener
    {
    /**
     * Called when we register with a given proxy.
     * 
     * @param client
     *      The client.
     * @param proxy
     *      The proxy.
     */
    void registered
            (URI client,
             URI proxy);

    /**
     * Called when we re-register with a given proxy.
     * 
     * @param client
     *      The client.
     * @param proxy
     *      The proxy.
     */
    void reRegistered
            (URI client,
             URI proxy);

    /**
     * Called when an attempt to register with a given proxy fails.
     * 
     * @param client
     *      The client.
     * @param proxy
     *      The proxy.
     */
    void registrationFailed
            (URI client,
             URI proxy);

    /**
     * Called when we unregister from a given proxy.
     * 
     * @param client
     *      The client.
     * @param proxy
     *      The proxy.
     */
    void unregistered
            (URI client,
             URI proxy);
    }
