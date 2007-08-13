package org.lastbamboo.common.sip.client;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ByteBuffer;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.sip.stack.message.DoubleCrlfKeepAlive;
import org.lastbamboo.common.sip.stack.message.Invite;
import org.lastbamboo.common.sip.stack.message.Register;
import org.lastbamboo.common.sip.stack.message.RequestTimeoutResponse;
import org.lastbamboo.common.sip.stack.message.SipMessage;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitor;
import org.lastbamboo.common.sip.stack.message.SipResponse;
import org.lastbamboo.common.sip.stack.message.UnknownSipRequest;
import org.lastbamboo.common.sip.stack.transaction.client.SipClientTransaction;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;
import org.lastbamboo.common.util.mina.MinaUtils;

/**
 * Class that visits incoming SIP messages for SIP clients.
 */
public class SipClientMessageVisitor implements SipMessageVisitor
    {

    private static final Log LOG = LogFactory.getLog(
        SipClientMessageVisitor.class);
    private final SipTransactionTracker m_transactionTracker;
    private final SipClient m_sipClient;
    private final OfferAnswerFactory m_offerAnswerFactory;
    
    /**
     * Visitor for message received on SIP clients.
     * 
     * @param sipClient The SIP client for writing any necessary messages.
     * @param tracker The tracker for looking up the corresponding transactions
     * for received messages.
     * @param offerAnswerFactory Class that processes incoming INVITEs.
     */
    public SipClientMessageVisitor(final SipClient sipClient,
        final SipTransactionTracker tracker,
        final OfferAnswerFactory offerAnswerFactory)
        {
        this.m_sipClient = sipClient;
        this.m_transactionTracker = tracker;
        this.m_offerAnswerFactory = offerAnswerFactory;
        }

    public void visitRequestTimedOut(final RequestTimeoutResponse response)
        {
        LOG.debug("Visiting request timed out response: "+response);
        notifyTransaction(response);
        }

    public void visitInvite(final Invite invite)
        {
        if (LOG.isDebugEnabled())
            {
            LOG.debug("Received invite: "+invite);
            }
        
        final ByteBuffer offer = invite.getBody();
        
        // Process the invite statelessly.
        try
            {
            final OfferAnswer offerAnswer = 
                this.m_offerAnswerFactory.createAnswerer(offer);
            final byte[] answer = offerAnswer.generateAnswer();
            this.m_sipClient.writeInviteOk(invite, ByteBuffer.wrap(answer));
            offerAnswer.processOffer(offer);
            }
        catch (final IOException e)
            {
            // This indicates the SDP contained data we could not understand,
            // so we need to send an error response.
            LOG.warn("We could not understand the offer: " +
                MinaUtils.toAsciiString(offer));
            // Generate a SIP error response.
            // See http://tools.ietf.org/html/rfc3261#section-13.3.1.3
            this.m_sipClient.writeInviteRejected(invite, 488, 
                "Not Acceptable Here");
            }
        }

    public void visitRegister(final Register register)
        {
        // Should never happen on UAS.
        LOG.error("Got REGISTER request on UAS -- weird: "+register);
        }

    public void visitDoubleCrlfKeepAlive(final DoubleCrlfKeepAlive keepAlive)
        {
        // Should never happen on UAS.
        LOG.error("Got keep-alive request on UAS -- weird: ");
        }
    
    public void visitUnknownRequest(final UnknownSipRequest request)
        {
        LOG.error("Unknown request on UAS: "+request);
        }

    public void visitResponse(final SipResponse response)
        {
        // Identify the transaction for this response and the corresponding 
        // session it creates.
        LOG.debug("Visiting OK response: "+ response);
        notifyTransaction(response);
        }
    
    
    private void notifyTransaction(final SipMessage response)
        {
        final SipClientTransaction ct = 
            this.m_transactionTracker.getClientTransaction(response);
        LOG.debug("Accessed transaction: "+ct);
        
        if (ct == null)
            {
            LOG.warn("No matching transaction for response: "+response);
            return;
            }
        
        response.accept(ct);
        }
    }
