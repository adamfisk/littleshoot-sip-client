package org.lastbamboo.common.sip.client;

/**
 * Interface for classes that calculate delays between CRLF keep-alive
 * send messages.  Useful for testing with artificial delays.
 */
public interface CrlfDelayCalculator
    {

    /**
     * Calculates the delay before the next CRLF keep-alive message.
     * 
     * @return The next delay in milliseconds.
     */
    int calculateDelay();

    }
