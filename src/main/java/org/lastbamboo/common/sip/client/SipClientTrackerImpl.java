package org.lastbamboo.common.sip.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lastbamboo.common.sip.client.util.ProxyRegistrationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for keeping track of SIP clients.
 */
public class SipClientTrackerImpl implements SipClientTracker
    {
    /**
     * The log for this class.
     */
    private final Logger m_log = LoggerFactory.getLogger(getClass()); 
    
    /**
     * The list of clients being tracked.
     */
    private final List<SipClient> m_clients = new ArrayList<SipClient>();
    
    /**
     * The mapping of clients to the registration listeners that need to be
     * notified when the clients are closed, since closing corresponds to the
     * registration being lost.
     */
    private final Map<SipClient, ProxyRegistrationListener> m_clientListenerMap =
        new HashMap<SipClient, ProxyRegistrationListener>();
    
    /**
     * The counter used to cycle through available clients.
     */
    private int m_counter = 0;
    
    /**
     * Creates a new class for keeping track of SIP client connections to proxy
     * servers.
     */
    public SipClientTrackerImpl()
        {
        }
    
    public SipClient getSipClient()
        {
        // Just keep cycling through the proxies.
        synchronized (this.m_clients)
            {
            ++this.m_counter;
            
            if (this.m_counter >= this.m_clients.size())
                {
                this.m_counter = 0;
                }
            
            if (this.m_clients.isEmpty())
                {
                m_log.warn("No available SIP clients!!");
                return null;
                }
            
            return this.m_clients.get(this.m_counter);
            }
        }

    public void addSipClient(final SipClient client, 
        final ProxyRegistrationListener listener)
        {
        m_log.debug("Adding SIP client: "+client);
        
        synchronized (this.m_clients)
            {   
            this.m_clients.add(client);
            this.m_clientListenerMap.put(client, listener);
            }
        }

    public void onClose(final SipClient client)
        {
        m_log.debug("Lost connection to the registrar...");
        
        synchronized (this.m_clients)
            {
            this.m_clients.remove(client);
            
            // We don't care about the reader/writer in this case.  It may be 
            // null. This indicates that we've lost the connection to the 
            // registrar.
            final ProxyRegistrationListener listener = 
                m_clientListenerMap.get(client);
            if (listener == null)
                {
                m_log.warn("No listener for client "+client+" map is: "+
                    this.m_clientListenerMap);
                return;
                }
            this.m_clientListenerMap.remove(client);
            
            // Notify the listener.
            listener.unregistered(client.getSipUri(), client.getProxyUri());
            }
        }
    }
