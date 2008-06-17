package org.lastbamboo.common.sip.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that continually sends keep alive messages to the SIP proxy, as 
 * specified in:<p>
 * 
 * http://www.ietf.org/internet-drafts/draft-ietf-sip-outbound-08.txt
 */
public class CrlfKeepAliveSender
    {

    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final SipClient m_sipClient;

    private final CrlfDelayCalculator m_delayCalculator;
    
    private volatile boolean m_stopped = false;
    
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
        m_log.debug("Stoping Keep Alive sender...");
        this.m_stopped = true;
        }
    
    /**
     * Continually schedules CRLF pings to send to the server.
     */
    public void scheduleCrlf()
        {
        final Runnable crlfRunner = new Runnable()
            {
            public void run()
                {
                while (!m_stopped)
                    {
                    final int delay = m_delayCalculator.calculateDelay();
                    try
                        {
                        Thread.sleep(delay);
                        m_sipClient.writeCrlfKeepAlive();
                        }
                    catch (final InterruptedException e)
                        {
                        m_log.error("Interrupted?", e);
                        }
                    }
                }
            };
        final Thread crlfThread = new Thread(crlfRunner, "CRLF-Thread");
        crlfThread.setDaemon(true);
        crlfThread.start();
        }
    }
