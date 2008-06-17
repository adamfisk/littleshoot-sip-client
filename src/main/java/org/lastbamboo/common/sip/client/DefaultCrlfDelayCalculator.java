package org.lastbamboo.common.sip.client;

import java.util.Random;

/**
 * Default implementation for calculating the delay between CRLF keep-alive
 * messages to the server.
 */
public class DefaultCrlfDelayCalculator implements CrlfDelayCalculator
    {

    private final Random m_rand = new Random();
    
    public int calculateDelay()
        {
        // For notes on the selection of time values, see section
        // 4.4.  Detecting Flow Failure of the SIP outbound draft at: 
        // http://www.ietf.org/internet-drafts/draft-ietf-sip-outbound-08.txt
        final int seconds = 120 - m_rand.nextInt(25);
        
        // Convert to milliseconds.
        return seconds * 1000;
        }

    }
