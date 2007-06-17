package org.lastbamboo.common.sip.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class that continually sends keep alive messages to the SIP proxy, as 
 * specified in:<p>
 * 
 * http://www.ietf.org/internet-drafts/draft-ietf-sip-outbound-08.txt
 */
public class CrlfKeepAliveSender
    {

    private final SipClient m_sipClient;

    private final ScheduledExecutorService m_executor = 
        Executors.newSingleThreadScheduledExecutor();

    private final CrlfDelayCalculator m_delayCalculator;
    
    /**
     * Creates a new class that uses the CRLF keep alive technique specified
     * in SIP outbound.
     * 
     * @param sipClient The SIP client.
     * @param delayCalculator Class for calculating delays between CRLF
     * messages. 
     */
    public CrlfKeepAliveSender(final SipClient sipClient,
        final CrlfDelayCalculator delayCalculator)
        {
        this.m_sipClient = sipClient;
        this.m_delayCalculator = delayCalculator;
        }
    
    /**
     * Stops this class from continuing to send keep alive messages to the 
     * server.
     */
    public void stop()
        {
        this.m_executor.shutdownNow();
        }
    
    /**
     * Continually schedules CRLF pings to send to the server.
     */
    public void scheduleCrlf()
        {
        final Runnable command = new Runnable()
            {
            public void run()
                {
                m_sipClient.writeCrlfKeepAlive();
                
                // Schedule the next one.
                scheduleCrlf();
                }
            };
            
        // This will schedule the CRLF send *once* after the calculated delay.
        final int delay = this.m_delayCalculator.calculateDelay();
        m_executor.schedule(command, delay, TimeUnit.MILLISECONDS);
        }
    }
