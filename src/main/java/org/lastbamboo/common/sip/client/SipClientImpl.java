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
import org.apache.commons.io.IOExceptionWithCause;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.sip.stack.IdleSipSessionListener;
import org.lastbamboo.common.sip.stack.codec.SipIoHandler;
import org.lastbamboo.common.sip.stack.codec.SipProtocolCodecFactory;
import org.lastbamboo.common.sip.stack.message.Invite;
import org.lastbamboo.common.sip.stack.message.Register;
import org.lastbamboo.common.sip.stack.message.SipMessage;
import org.lastbamboo.common.sip.stack.message.SipMessageFactory;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitorFactory;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactory;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactoryImpl;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayer;
import org.lastbamboo.common.sip.stack.util.UriUtils;
import org.littleshoot.mina.common.ByteBuffer;
import org.littleshoot.mina.common.ConnectFuture;
import org.littleshoot.mina.common.IoConnector;
import org.littleshoot.mina.common.IoConnectorConfig;
import org.littleshoot.mina.common.IoFuture;
import org.littleshoot.mina.common.IoFutureListener;
import org.littleshoot.mina.common.IoHandler;
import org.littleshoot.mina.common.IoService;
import org.littleshoot.mina.common.IoServiceConfig;
import org.littleshoot.mina.common.IoServiceListener;
import org.littleshoot.mina.common.IoSession;
import org.littleshoot.mina.common.RuntimeIOException;
import org.littleshoot.mina.common.SimpleByteBufferAllocator;
import org.littleshoot.mina.common.ThreadModel;
import org.littleshoot.mina.common.WriteFuture;
import org.littleshoot.mina.filter.codec.ProtocolCodecFilter;
import org.littleshoot.mina.filter.executor.ExecutorFilter;
import org.littleshoot.mina.transport.socket.nio.SocketConnector;
import org.littleshoot.mina.transport.socket.nio.SocketConnectorConfig;
import org.littleshoot.util.DaemonThreadFactory;
import org.littleshoot.util.NetworkUtils;
import org.littleshoot.util.SessionSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a new SIP client.  This class supplies the interface for writing all
 * SIP messages.  It queues up messages on a separate thread from in-order
 * writing.
 */
public class SipClientImpl implements SipClient,
        OfferAnswerTransactionListener, IoFutureListener, IoServiceListener {

    private final Logger m_log = LoggerFactory.getLogger(getClass());

    private final UUID m_instanceId = UUID.randomUUID();

    private final Object REGISTER_OK_LOCK = new Object();

    private volatile boolean m_receivedResponse;

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

    private volatile boolean m_registrationSucceeded;

    /**
     * The executor is used to queue up messages in order. This allows different
     * threads to send messages without worrying about them getting mangled or
     * out of order.
     */
    private final ExecutorService m_messageExecutor = Executors
            .newSingleThreadExecutor();

    /**
     * Thread pool for read/write operations for the SIP client as well as for
     * processing of actual messages -- we use the same thread pool for both.
     * See: http://mina.apache.org/configuring-thread-model.html
     */
    private final ExecutorService m_acceptingExecutor = Executors
            .newCachedThreadPool(new DaemonThreadFactory(
                    "SIP-Client-Thread-Pool"));

    private final CrlfKeepAliveSender m_crlfKeepAliveSender;

    private volatile boolean m_closed;

    private IoSession m_ioSession;

    private int m_responsesWritten = 0;

    private final OfferAnswerFactory m_offerAnswerFactory;

    private final IdleSipSessionListener m_idleSipSessionListener;

    private final SessionSocketListener socketListener;

    /**
     * Creates a new SIP client connection to an individual SIP proxy server.
     * 
     * @param sipClientUri
     *            The URI of the client.
     * @param proxyUri
     *            The URI of the proxy.
     * @param messageFactory
     *            The factory for creating new SIP messages.
     * @param transactionTracker
     *            The class for keeping track of SIP client transactions.
     * @param offerAnswerFactory
     *            Factory for creating classes capable of handling offers and
     *            answers.
     * @param socketListener
     *            The listener for incoming sockets on the answerer.
     * @param uriUtils
     *            Utilities for handling SIP URIs.
     * @param transportLayer
     *            The class for actually sending SIP messages.
     * @param closeListener
     *            The class that listens for closed connections to proxies.
     * @param calculator
     *            The class that calculates the delay between double CRLF
     *            keep-alive messages, passed in for testing.
     * @param idleSipSessionListener
     *            Listener for idle SIP sessions.
     */
    public SipClientImpl(final URI sipClientUri, final URI proxyUri,
            final SipMessageFactory messageFactory,
            final SipTransactionTracker transactionTracker,
            final OfferAnswerFactory offerAnswerFactory,
            final SessionSocketListener socketListener,
            final UriUtils uriUtils, final SipTcpTransportLayer transportLayer,
            final SipClientCloseListener closeListener,
            final CrlfDelayCalculator calculator,
            final IdleSipSessionListener idleSipSessionListener) {
        this.socketListener = socketListener;
        // Configure the MINA buffers for optimal performance.
        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        try {
            this.m_address = NetworkUtils.getLocalHost();
        } catch (final UnknownHostException e) {
            m_log.error("Could not resolve localhost", e);
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
        try {
            this.m_contactUri = createContactUri(sipClientUri);
            this.m_domainUri = new URI("sip:lastbamboo.org");
        } catch (final URISyntaxException e) {
            m_log.error("Could not create URI", e);
            throw new IllegalArgumentException("Bad URI: " + sipClientUri);
        }

        this.m_crlfKeepAliveSender = new CrlfKeepAliveSender(this, calculator);
        this.m_idleSipSessionListener = idleSipSessionListener;
    }

    public void connect() throws IOException {
        final String host = this.m_uriUtils.getHostInSipUri(this.m_proxyUri);
        final int port = this.m_uriUtils.getPortInSipUri(this.m_proxyUri);
        final InetSocketAddress remoteAddress = new InetSocketAddress(host,
                port);

        m_log.debug("Connecting to registrar at: " + remoteAddress);
        this.m_ioSession = connect(remoteAddress);
    }

    public void register() throws IOException {
        register(this.m_sipClientUri, this.m_domainUri, this.m_ioSession);
        this.m_crlfKeepAliveSender.scheduleCrlf();
    }

    private IoSession connect(final InetSocketAddress remoteAddress)
            throws IOException {
        final SipMessageVisitorFactory visitorFactory = 
            new SipClientMessageVisitorFactory(
                this, this.m_transactionTracker, this.m_offerAnswerFactory,
                this.socketListener);

        final SipHeaderFactory headerFactory = new SipHeaderFactoryImpl();

        // The thread model configuration is very important here. The SIP
        // "client" is ultimately acting as a gateway to the HTTP server, so
        // the thread configuration should be a server configuration.
        final IoConnector connector = new SocketConnector(4,
                this.m_acceptingExecutor);
        connector.addListener(this);

        final IoConnectorConfig config = new SocketConnectorConfig();

        // final ThreadModel threadModel =
        // ExecutorThreadModel.getInstance("SIP-Client-MINA");
        config.setThreadModel(ThreadModel.MANUAL);

        connector.getFilterChain().addLast(
                "codec",
                new ProtocolCodecFilter(new SipProtocolCodecFactory(
                        headerFactory)));
        connector.getFilterChain().addLast("threadPool",
                new ExecutorFilter(this.m_acceptingExecutor));

        final IoHandler handler = new SipIoHandler(visitorFactory,
                this.m_idleSipSessionListener);

        final ConnectFuture future = connector.connect(remoteAddress, handler,
                config);
        future.join();

        if (!future.isConnected()) {
            m_log.error("Could not connect to server at: " + remoteAddress);
            throw new IOException("Could not connect to server at: "
                    + remoteAddress);
        }

        final IoSession session;
        try {
            session = future.getSession();
        } catch (final RuntimeIOException e) {
            // This seems to get thrown when we can't connect at all.
            m_log.warn("Could not connect to SIP server at: " + remoteAddress,
                    e);
            throw new IOExceptionWithCause("Couldn't connect to SIP server", e);
        }
        if (session == null) {
            throw new IOException("Could not connect to server at: "
                    + remoteAddress);
        }
        m_log.debug("Successfully connected to the SIP server!");
        return session;
    }

    private URI createContactUri(final URI sipClientUri)
            throws URISyntaxException {
        final String user = this.m_uriUtils.getUserInSipUri(sipClientUri);
        final String address = this.m_address.getHostAddress();
        final String contactUriString = "sip:" + user + "@" + address;
        return new URI(contactUriString);
    }

    public void writeCrlfKeepAlive() {
        if (this.m_closed) {
            m_log.debug("Ignoring CRLF call on closed SIP client.");
            return;
        }
        final Runnable runner = new Runnable() {
            public void run() {
                try {
                    final WriteFuture wf = m_transportLayer
                            .writeCrlfKeepAlive(m_ioSession);
                    wf.addListener(new IoFutureListener() {
                        public void operationComplete(final IoFuture future) {
                            m_log.debug("Finished writing CRLF...");
                        }
                    });
                } catch (final Throwable t) {
                    m_log.error("Caught throwable", t);
                }
            }
        };

        this.m_messageExecutor.execute(runner);
    }

    public void offer(final URI sipUri, final byte[] body,
            final OfferAnswerTransactionListener listener) {
        m_log.info("Sending offer to SIP URI: {}", sipUri);
        final Runnable runner = new Runnable() {
            public void run() {
                try {
                    final Invite request = m_messageFactory
                            .createInviteRequest("Anonymous", sipUri,
                                    m_sipClientUri, m_instanceId, m_contactUri,
                                    ByteBuffer.wrap(body));

                    m_transportLayer.invite(request, m_ioSession, listener);
                } catch (final Throwable t) {
                    m_log.error("Unexpected throwable", t);
                }
            }
        };

        this.m_messageExecutor.execute(runner);
    }

    public UUID getInstanceId() {
        return this.m_instanceId;
    }

    public URI getContactUri() {
        return this.m_contactUri;
    }

    public URI getSipUri() {
        return this.m_sipClientUri;
    }

    public URI getProxyUri() {
        return this.m_proxyUri;
    }

    private void register(final URI client, final URI domain,
            final IoSession session) throws IOException {
        final Register request = this.m_messageFactory.createRegisterRequest(
                domain, "Anonymous", client, this.m_instanceId,
                this.m_contactUri);

        waitForRegisterResponse(request, session);
    }

    private void waitForRegisterResponse(final Register request,
            final IoSession session) throws IOException {
        synchronized (REGISTER_OK_LOCK) {
            this.m_receivedResponse = false;
            this.m_transportLayer.register(request, session, this);

            if (!this.m_receivedResponse) {
                try {
                    REGISTER_OK_LOCK.wait(20 * 1000);
                    if (!this.m_receivedResponse) {
                        m_log.error("Did not get response after waiting");
                        throw new IOException("Did not get response!!");
                    }
                } catch (final InterruptedException e) {
                    m_log.error("Somehow interrupted!!", e);
                    throw new IOException("Did not get response!!");
                }
            }

            if (!this.m_registrationSucceeded) {
                m_log.warn("Could not register!!");
                throw new IOException("Registration failed!!");
            }
        }
    }

    public void onTransactionSucceeded(final OfferAnswerMessage message) {
        m_log.debug("Received OK response to register request: {}", message);
        synchronized (REGISTER_OK_LOCK) {
            this.m_receivedResponse = true;
            this.m_registrationSucceeded = true;
            REGISTER_OK_LOCK.notify();
        }
    }

    public void onTransactionFailed(final OfferAnswerMessage message) {
        m_log.warn("Received non-OK response to register request: {}", message);
        synchronized (REGISTER_OK_LOCK) {
            this.m_receivedResponse = true;
            this.m_registrationSucceeded = false;
            REGISTER_OK_LOCK.notify();
        }
    }

    public void operationComplete(final IoFuture future) {
        m_responsesWritten++;
        if (m_log.isDebugEnabled()) {
            m_log.debug("Now written " + m_responsesWritten + " responses...");
        }
    }

    public void writeInviteOk(final Invite invite, final ByteBuffer body) {
        final SipMessage response = this.m_messageFactory.createInviteOk(
                invite, getInstanceId(), getContactUri(), body);

        writeResponse(response);
    }

    public void writeInviteRejected(final Invite invite,
            final int responseCode, final String reasonPhrase) {
        final SipMessage response = this.m_messageFactory.createErrorResponse(
                invite, getInstanceId(), getContactUri(), responseCode,
                reasonPhrase);

        writeResponse(response);
    }

    private void writeResponse(final SipMessage message) {
        if (m_log.isDebugEnabled()) {
            m_log.debug("Sending SIP response...");
        }

        final Runnable runner = new Runnable() {

            public void run() {
                // Note there is no Via handling here. This is for UASes
                // sending responses, so we don't need to strip any Vias.
                try {
                    final WriteFuture wf = m_ioSession.write(message);
                    wf.addListener(SipClientImpl.this);
                } catch (final Throwable t) {
                    m_log.error("Unexpected throwable", t);
                }
            }
        };

        this.m_messageExecutor.execute(runner);
    }

    public void serviceActivated(final IoService service,
            final SocketAddress serviceAddress, final IoHandler handler,
            final IoServiceConfig config) {
        m_log.debug("Service activated.");
    }

    public void serviceDeactivated(final IoService service,
            final SocketAddress serviceAddress, final IoHandler handler,
            final IoServiceConfig config) {
        m_log.debug("Service deactivated.");
    }

    public void sessionCreated(final IoSession session) {
        m_log.debug("Session created.");
    }

    public void sessionDestroyed(final IoSession session) {
        m_log.debug("Lost connection to the registrar...notifying listener...");
        this.m_closed = true;
        this.m_crlfKeepAliveSender.stop();
        this.m_messageExecutor.shutdown();
        this.m_acceptingExecutor.shutdown();
        this.m_closeListener.onClose(this);
    }
}
