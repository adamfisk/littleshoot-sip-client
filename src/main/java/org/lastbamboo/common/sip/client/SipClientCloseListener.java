package org.lastbamboo.common.sip.client;

/**
 * Interface for classes wishing to be notified when we lose connections to
 * SIP servers.
 */
public interface SipClientCloseListener
    {

    /**
     * Called when the specified SIP client connection to a server is lost.
     * 
     * @param client The client connection that has closed.
     */
    void onClose(final SipClient client);

    }
