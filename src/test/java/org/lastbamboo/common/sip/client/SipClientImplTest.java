package org.lastbamboo.common.sip.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;

import junit.framework.TestCase;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ByteBuffer;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.MediaOfferAnswer;
import org.lastbamboo.common.sip.client.stubs.OfferAnswerStub;
import org.lastbamboo.common.sip.client.stubs.MediaOfferAnswerStub;
import org.lastbamboo.common.sip.stack.SipUriFactory;
import org.lastbamboo.common.sip.stack.SipUriFactoryImpl;
import org.lastbamboo.common.sip.stack.message.SipMessage;
import org.lastbamboo.common.sip.stack.message.SipMessageFactory;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionListener;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayer;
import org.lastbamboo.common.sip.stack.util.UriUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Tests a SIP client continually registering and sending messages.
 */
public class SipClientImplTest extends TestCase 
    implements SipTransactionListener
    {

    private static final Log LOG = LogFactory.getLog(SipClientImplTest.class);

    private static final int NUM_INVITES = 100;
    
    private final int TEST_PORT = 8472;

    private SipUriFactory m_sipUriFactory;

    private int m_invitesReceivedOnServer;
    
    /**
     * Test to make sure the client is just sending out INVITEs that are
     * received intact on the server.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    public void testSipClientInvites() throws Exception
        {
        startServerThread();
        m_sipUriFactory = new SipUriFactoryImpl();
        final SipClient client = createSipClient();
        
        final URI invitee = m_sipUriFactory.createSipUri(42798L);
        final byte[] body = new byte[0];
        for (int i = 0; i < NUM_INVITES; i++)
            {
            client.invite(invitee, body, this);
            }
        
        if (m_invitesReceivedOnServer < NUM_INVITES)
            {
            synchronized(this)
                {
                wait(20*1000);
                }
            }
        
        if (m_invitesReceivedOnServer < NUM_INVITES)
            {
            fail("Only recieved "+m_invitesReceivedOnServer+
                " invites on server...");
            }
        }

    private SipClient createSipClient() throws Exception
        {
        final String[] contexts = 
            {
            "sipStackBeans.xml", "sipClientBeans.xml", "utilBeans.xml", 
            };
        
        LOG.debug("Loading contexts...");
        final ClassPathXmlApplicationContext context = 
            new ClassPathXmlApplicationContext(contexts);
        LOG.debug("Loaded contexts...");
        
        final long userId = 48392L;
        final URI clientUri = m_sipUriFactory.createSipUri (userId);

        final URI proxyUri = 
            new URI("sip:127.0.0.1:"+TEST_PORT+";transport=tcp");
        
        final SipMessageFactory messageFactory = 
            (SipMessageFactory) context.getBean("sipMessageFactory");
        final SipTransactionTracker transactionTracker = 
            (SipTransactionTracker) context.getBean("sipTransactionTracker");
        
        final UriUtils uriUtils = (UriUtils) context.getBean("uriUtils");
        final SipTcpTransportLayer transportLayer = 
            (SipTcpTransportLayer) context.getBean("sipTransportLayer");
         
        final OfferAnswerFactory offerAnswerFactory = new OfferAnswerFactory()
            {
            public OfferAnswer createOfferer()
                {
                return new OfferAnswerStub();
                }

            public MediaOfferAnswer createAnswerer(ByteBuffer offer)
                {
                return new MediaOfferAnswerStub();
                }

            public MediaOfferAnswer createMediaOfferer()
                {
                return new MediaOfferAnswerStub();
                }
            };
            
        final SipClientTracker sipClientTracker = 
            (SipClientTracker) context.getBean("sipClientTracker");
        
        final CrlfDelayCalculator calculator = new DefaultCrlfDelayCalculator();
        final OfferAnswerListener offerAnswerListener = 
            new OfferAnswerListener()
            {
            public void onOfferAnswerComplete(final MediaOfferAnswer offerAnswer)
                {
                }

            public void onOfferAnswerFailed(MediaOfferAnswer mediaOfferAnswer)
                {
                }
            };
        final SipClient client = 
            new SipClientImpl(clientUri, proxyUri, 
                messageFactory, transactionTracker, offerAnswerFactory, 
                offerAnswerListener, uriUtils, transportLayer, 
                sipClientTracker, calculator, null);
       
        client.connect();
        client.register();
        return client;
        }

    private void startServerThread()
        {
        final Runnable runner = new Runnable()
            {
            public void run()
                {
                try
                    {
                    startServer();
                    }
                catch (final IOException e)
                    {
                    SipClientImplTest.fail("Could not start server");
                    }
                }
            };
        final Thread serverThread = new Thread(runner, "server-thread");
        serverThread.setDaemon(true);
        serverThread.start();
        }

    private void startServer() throws IOException
        {
        final ServerSocket server = new ServerSocket(TEST_PORT);
        final Socket sock = server.accept();
        
        LOG.debug("Got server socket!!!");
        
        final InputStream is = sock.getInputStream();
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        
        String curLine = br.readLine();
        if (curLine == null)
            {
            LOG.error("GOT NULL LINE!!");
            //is.close();
            //sock.getOutputStream().close();
            sock.close();
            return;
            }
        /*
        if (StringUtils.isEmpty(curLine))
            {
            while (StringUtils.isEmpty(curLine))
                {
                LOG.debug("Got blank line");
                curLine = br.readLine();
                }
            }
            */
        final boolean giveRegisterOk;
        if (curLine.startsWith("REGISTER"))
            {
            giveRegisterOk = true;
            }
        else if (!curLine.startsWith("INVITE"))
            {
            fail("No REGISTER or INVITE: "+curLine);
            giveRegisterOk = false;
            }
        else
            {
            giveRegisterOk = false;
            }
        
        String branch = "";
        while (!StringUtils.isBlank(curLine))
            {
            LOG.debug(curLine);
            curLine = br.readLine();
            if (curLine.startsWith("Via"))
                {
                branch = StringUtils.substringAfter(curLine, "branch=");
                }
            }
        if (giveRegisterOk)
            {
            final OutputStream os = sock.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write("SIP/2.0 200 OK\r\n");
            writer.write("To: Anonymous <sip:1199792423@lastbamboo.org>;tag=2365b467-5\r\n");
            writer.write("Via: SIP/2.0/TCP 10.250.77.172:5060;branch="+branch+"\r\n");
            writer.write("Supported: outbound\r\n");
            writer.write("CSeq: 2 REGISTER\r\n");
            writer.write("Call-ID: d0d08c9-\r\n");
            writer.write("From: Anonymous <sip:1199792423@lastbamboo.org>;tag=0244d706-5\r\n\r\n");
           
            writer.flush();
            os.flush();
            }
        
        curLine = br.readLine();
        while (true)
            {
            LOG.debug(curLine);
            if (curLine.startsWith("INVITE"))
                {
                m_invitesReceivedOnServer++;
                }
            curLine = br.readLine();
            
            if (m_invitesReceivedOnServer == NUM_INVITES)
                {
                synchronized(this)
                    {
                    this.notify();
                    }
                break;
                }
            }
        }

    public void onTransactionFailed(SipMessage arg0)
        {
        LOG.debug("Transaction failed...");
        }

    public void onTransactionSucceeded(SipMessage arg0)
        {
        LOG.debug("Transaction succeeded...");
        }
    }
