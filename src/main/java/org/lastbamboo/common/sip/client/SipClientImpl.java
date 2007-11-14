package org.lastbamboo.common.sip.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.id.uuid.UUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoConnectorConfig;
import org.apache.mina.common.IoFuture;
import org.apache.mina.common.IoFutureListener;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoService;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.IoServiceListener;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.common.ThreadModel;
import org.apache.mina.common.WriteFuture;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.sip.stack.codec.SipIoHandler;
import org.lastbamboo.common.sip.stack.codec.SipProtocolCodecFactory;
import org.lastbamboo.common.sip.stack.message.Invite;
import org.lastbamboo.common.sip.stack.message.Register;
import org.lastbamboo.common.sip.stack.message.SipMessage;
import org.lastbamboo.common.sip.stack.message.SipMessageFactory;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitorFactory;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactory;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactoryImpl;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionListener;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayer;
import org.lastbamboo.common.sip.stack.util.UriUtils;
import org.lastbamboo.common.util.NetworkUtils;

/**
 * Creates a new SIP client.  This class supplies the interface for writing all
 * SIP messages.  It queues up messages on a separate thread from in-order
 * writing.
 */
public class SipClientImpl implements SipClient,
    SipTransactionListener, IoFutureListener, IoServiceListener
    {

    private final Log LOG = LogFactory.getLog(SipClientImpl.class);
    
    private final UUID m_instanceId = UUID.randomUUID();
    
    private final Object REGISTER_OK_LOCK = new Object();
    
    private boolean m_receivedResponse;

    private final URI m_sipClientUri;

    private final URI m_proxyUri;
    
    private final URI m_domainUri;

    private final URI m_contactUri;

    private final SipMessageFactory m_messageFactory;

    private final InetAddress m_address;

    private final UriUtils m_uriUtils;

    private final SipTransactionTracker m_transactionTracker;

    private final SipTcpTransportLayer m_transportLayer;

    private final SipClientCloseListener m_closeListener;

    private boolean m_registrationSucceeded;
    
    /**
     * The executor is used to queue up messages in order.  This allows 
     * different threads to send messages without worrying about them getting
     * mangled or out of order.
     */
    private final ExecutorService m_messageExecutor = 
        Executors.newSingleThreadExecutor();

    private final CrlfKeepAliveSender m_crlfKeepAliveSender;

    private volatile boolean m_closed;

    private IoSession m_ioSession;
    
    private int m_responsesWritten = 0;

    private final OfferAnswerFactory m_offerAnswerFactory;

    private final OfferAnswerListener m_offerAnswerListener;
    
    /**
     * Creates a new SIP client connection to an individual SIP proxy server.
     * 
     * @param sipClientUri The URI of the client.
     * @param proxyUri The URI of the proxy.
     * @param messageFactory The factory for creating new SIP messages.
     * @param transactionTracker The class for keeping track of SIP client
     * transactions.
     * @param offerAnswerFactory Factory for creating classes capable of 
     * handling offers and answers.
     * @param offerAnswerListener The listener for offer/answer events.
     * @param uriUtils Utilities for handling SIP URIs.
     * @param transportLayer The class for actually sending SIP messages.
     * @param closeListener The class that listens for closed connections to
     * proxies.
     * @param calculator The class that calculates the delay between double 
     * CRLF keep-alive messages, passed in for testing.
     */
    public SipClientImpl(final URI sipClientUri, final URI proxyUri,
        final SipMessageFactory messageFactory, 
        final SipTransactionTracker transactionTracker,
        final OfferAnswerFactory offerAnswerFactory,
        final OfferAnswerListener offerAnswerListener,
        final UriUtils uriUtils, 
        final SipTcpTransportLayer transportLayer,
        final SipClientCloseListener closeListener, 
        final CrlfDelayCalculator calculator) 
        {
        m_offerAnswerListener = offerAnswerListener;
        // Configure the MINA buffers for optimal performance.
        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        
        try
            {
            this.m_address = NetworkUtils.getLocalHost();
            }
        catch (final UnknownHostException e)
            {
            LOG.error("Could not resolve localhost", e);
            throw new IllegalArgumentException("Could not resolve localhost");
            }
        this.m_sipClientUri = sipClientUri;
        this.m_proxyUri = proxyUri;  
        this.m_messageFactory = messageFactory;
        this.m_transactionTracker = transactionTracker;
        this.m_offerAnswerFactory = offerAnswerFactory;
        this.m_uriUtils = uriUtils;
        this.m_transportLayer = transportLayer;
        this.m_closeListener = closeListener;
        try
            {
            this.m_contactUri = createContactUri(sipClientUri);
            this.m_domainUri = new URI("sip:lastbamboo.org");
            }
        catch (final URISyntaxException e)
            {
            LOG.error("Could not create URI", e);
            throw new IllegalArgumentException("Bad URI: "+sipClientUri);
            }

        this.m_crlfKeepAliveSender = new CrlfKeepAliveSender(this, calculator);
        }
    
    public void connect() throws IOException
        {
        final String host = this.m_uriUtils.getHostInSipUri(this.m_proxyUri);  
        final int port = this.m_uriUtils.getPortInSipUri(this.m_proxyUri);
        final InetSocketAddress remoteAddress = 
            new InetSocketAddress(host, port);
        
        LOG.debug("Connecting to registrar at: "+remoteAddress);
        this.m_ioSession = connect(remoteAddress);
        }
    
    public void register() throws IOException 
        {
        register(this.m_sipClientUri, this.m_domainUri, this.m_ioSession);
        this.m_crlfKeepAliveSender.scheduleCrlf();
        }
    
    private IoSession connect(final InetSocketAddress remoteAddress) 
        throws IOException
        {
        final SipMessageVisitorFactory visitorFactory = 
            new SipClientMessageVisitorFactory(this, this.m_transactionTracker, 
                this.m_offerAnswerFactory, this.m_offerAnswerListener);
        
        final SipHeaderFactory headerFactory = new SipHeaderFactoryImpl();

        final IoConnector connector = new SocketConnector();
        connector.addListener(this);
        
        final IoConnectorConfig config = new SocketConnectorConfig();
        final ThreadModel threadModel = 
            ExecutorThreadModel.getInstance("SIP-Client-MINA");
        config.setThreadModel(threadModel);
        
        connector.getFilterChain().addLast("codec",
            new ProtocolCodecFilter(new SipProtocolCodecFactory(headerFactory)));
        
        final IoHandler handler = new SipIoHandler(visitorFactory);
        
        final ConnectFuture future = 
            connector.connect(remoteAddress, handler, config);
        
        future.join();
        
        if (!future.isConnected())
            {
            LOG.error("Could not connect to server at: "+remoteAddress);
            throw new IOException("Could not connect to server at: "+
                remoteAddress);
            }
        final IoSession session = future.getSession();
        if (session == null)
            {
            throw new IOException("Could not connecto to server at: "+
                remoteAddress);
            }
        if (LOG.isDebugEnabled())
            {
            LOG.debug("Successfully connected to the SIP server!");
            }
        return session;
        }
    

    private URI createContactUri(final URI sipClientUri) 
        throws URISyntaxException
        {
        final String user = this.m_uriUtils.getUserInSipUri(sipClientUri);
        final String address = this.m_address.getHostAddress();
        final String contactUriString = "sip:"+user+"@"+address;
        return new URI(contactUriString);
        }

    public void writeCrlfKeepAlive()
        {
        if (this.m_closed)
            {
            if (LOG.isDebugEnabled())
                {
                LOG.debug("Ignoring CRLF call on closed SIP client.");
                }
            return;
            }
        final Runnable runner = new Runnable()
            {
            public void run()
                {
                m_transportLayer.writeCrlfKeepAlive(m_ioSession);
                }
            };

        this.m_messageExecutor.execute(runner);
        }
    
    public void invite(final URI sipUri, 
        final byte[] body, final SipTransactionListener listener) 
        throws IOException
        {
        final Runnable runner = new Runnable()
            {

            public void run()
                {
                final Invite request = m_messageFactory.createInviteRequest(
                    "Anonymous", sipUri, m_sipClientUri, m_instanceId, 
                    m_contactUri, ByteBuffer.wrap(body));
                
                m_transportLayer.invite(request, m_ioSession, listener);
                }
            };
        
        this.m_messageExecutor.execute(runner);
        }

    public UUID getInstanceId()
        {
        return this.m_instanceId;
        }

    public URI getContactUri()
        {
        return this.m_contactUri;
        }
    
    public URI getSipUri()
        {
        return this.m_sipClientUri;
        }
    
    public URI getProxyUri()
        {
        return this.m_proxyUri;
        }
    
    private void register(final URI client, final URI domain,
        final IoSession session) throws IOException
        {
        final Register request = this.m_messageFactory.createRegisterRequest(
            domain, "Anonymous", client, this.m_instanceId, 
            this.m_contactUri);
        
        waitForRegisterResponse(request, session, REGISTER_OK_LOCK);
        }
    
    private void waitForRegisterResponse(final Register request, 
        final IoSession session, final Object lock) throws IOException
        {
        synchronized (lock)
            {
            this.m_receivedResponse = false;
            this.m_transportLayer.register(request, session, this);
            
            if (!this.m_receivedResponse)
                {
                try
                    {
                    lock.wait(10 * 1000);
                    if (!this.m_receivedResponse)
                        {
                        throw new IOException("Did not get response!!");
                        }
                    }
                catch (final InterruptedException e)
                    {
                    LOG.error("Somehow interrupted!!", e);
                    throw new IOException("Did not get response!!");
                    }
                }
            
            if (!this.m_registrationSucceeded)
                {
                LOG.warn("Could not register!!");
                throw new IOException("Registration failed!!");
                }
            }
        }

    public void onTransactionSucceeded(final SipMessage message)
        {
        LOG.debug("Received OK response to register request: "+message);
        synchronized (REGISTER_OK_LOCK)
            {
            this.m_receivedResponse = true;
            this.m_registrationSucceeded = true;
            REGISTER_OK_LOCK.notify();
            }
        }

    public void onTransactionFailed(final SipMessage message)
        {
        LOG.warn("Received non-OK response to register request: "+message);
        synchronized (REGISTER_OK_LOCK)
            {
            this.m_receivedResponse = true;
            this.m_registrationSucceeded = false;
            REGISTER_OK_LOCK.notify();
            }
        }

    public void operationComplete(final IoFuture future)
        {
        m_responsesWritten++;
        if (LOG.isDebugEnabled())
            {
            LOG.debug("Now written "+m_responsesWritten+" responses...");
            }
        }

    public void writeInviteOk(final Invite invite, final ByteBuffer body) 
        {
        final SipMessage response = 
            this.m_messageFactory.createInviteOk(invite, getInstanceId(), 
                getContactUri(), body);
    
        writeResponse(response);
        }
    
    public void writeInviteRejected(final Invite invite, 
        final int responseCode, final String reasonPhrase) 
        {
        final SipMessage response = 
            this.m_messageFactory.createErrorResponse(invite, getInstanceId(), 
                getContactUri(), responseCode, reasonPhrase);
    
        writeResponse(response);
        }

    private void writeResponse(final SipMessage message)
        {
        if (LOG.isDebugEnabled())
            {
            LOG.debug("Sending SIP response...");
            }
        
        final Runnable runner = new Runnable()
            {
    
            public void run()
                {
                // Note there is no Via handling here.  This is for UASes 
                // sending responses, so we don't need to strip any Vias.                
                final WriteFuture wf = m_ioSession.write(message);
                wf.addListener(SipClientImpl.this);
                }
            };
    
        this.m_messageExecutor.execute(runner);
        }

    public void serviceActivated(final IoService service, 
        final SocketAddress serviceAddress, final IoHandler handler, 
        final IoServiceConfig config)
        {
        LOG.debug("Service activated.");
        }

    public void serviceDeactivated(final IoService service, 
        final SocketAddress serviceAddress, final IoHandler handler, 
        final IoServiceConfig config)
        {
        LOG.warn("Service deactivated.");
        }

    public void sessionCreated(final IoSession session)
        {
        LOG.debug("Session created.");
        }

    public void sessionDestroyed(IoSession session)
        {
        LOG.warn("Lost connection to the registrar...notifying listener...");
        this.m_closed = true;
        this.m_crlfKeepAliveSender.stop();
        this.m_messageExecutor.shutdownNow();
        this.m_closeListener.onClose(this);
        }
    }
