package org.lastbamboo.common.sip.client;

/**
 * Default implementation for calculating the delay between CRLF keep-alive
 * messages to the server.
 */
public class DefaultCrlfDelayCalculator implements CrlfDelayCalculator
    {

    public int calculateDelay()
        {
        // For notes on the selection of time values, see section
        // 4.4.  Detecting Flow Failure of the SIP outbound draft at: 
        // http://www.ietf.org/internet-drafts/draft-ietf-sip-outbound-08.txt
        final double rand = Math.random();
        
        final int seconds = 120 - (int)(rand * 25);
        return seconds * 1000;
        }

    }
