package org.lastbamboo.common.sip.client;

import org.lastbamboo.common.sip.client.util.ProxyRegistrationListener;

/**
 * Keeps track of SIP client instances.  Each SIP client has a connection to
 * a single SIP server.
 */
public interface SipClientTracker extends SipClientCloseListener
    {

    /**
     * Accesses a random SIP client connection to a SIP server from the 
     * available clients.
     * 
     * @return A random SIP client from the available client connections
     * to SIP servers.  If there are no available connections to proxies this
     * returns <code>null</code>.
     */
    SipClient getSipClient();
    
    /**
     * Adds a SIP client to the tracker.
     * 
     * @param client
     *      The client to track.
     * @param listener
     *      The registration listener that is notified when the client is
     *      closed.  The closing of the client signifies that the registration
     *      is lost.
     */
    void addSipClient
            (SipClient client,
             ProxyRegistrationListener listener);

    }
