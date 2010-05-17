package org.lastbamboo.common.sip.client;

import org.littleshoot.mina.common.ByteBuffer;
import org.lastbamboo.common.offer.answer.MediaOfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that visits incoming SIP messages for SIP clients.
 */
public class SipClientMessageVisitor implements SipMessageVisitor
    {

    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final SipTransactionTracker m_transactionTracker;
    private final SipClient m_sipClient;
    private final OfferAnswerFactory m_offerAnswerFactory;
    private final OfferAnswerListener m_offerAnswerListener;
    
    /**
     * Visitor for message received on SIP clients.
     * 
     * @param sipClient The SIP client for writing any necessary messages.
     * @param tracker The tracker for looking up the corresponding transactions
     * for received messages.
     * @param offerAnswerFactory Class that processes incoming INVITEs.
     * @param offerAnswerListener 
     */
    public SipClientMessageVisitor(final SipClient sipClient,
        final SipTransactionTracker tracker,
        final OfferAnswerFactory offerAnswerFactory, 
        final OfferAnswerListener offerAnswerListener)
        {
        this.m_sipClient = sipClient;
        this.m_transactionTracker = tracker;
        this.m_offerAnswerFactory = offerAnswerFactory;
        this.m_offerAnswerListener = offerAnswerListener;
        }

    public void visitRequestTimedOut(final RequestTimeoutResponse response)
        {
        m_log.debug("Visiting request timed out response: "+response);
        notifyTransaction(response);
        }

    public void visitInvite(final Invite invite)
        {
        m_log.debug("Received invite: {}", invite);
        
        final ByteBuffer offer = invite.getBody();
        
        // Process the invite.
        final MediaOfferAnswer offerAnswer;
        try
            {
            offerAnswer = this.m_offerAnswerFactory.createAnswerer(offer);
            }
        catch (final OfferAnswerConnectException e)
            {
            // This indicates we could not establish the necessary connections 
            // for generating our candidates.
            m_log.warn("We could not create candidates for offer: " +
                MinaUtils.toAsciiString(offer), e);
            // Generate a SIP error response.
            // See http://tools.ietf.org/html/rfc3261#section-13.3.1.3
            this.m_sipClient.writeInviteRejected(invite, 488, 
                "Not Acceptable Here");
            return;
            }
        final byte[] answer = offerAnswer.generateAnswer();
        this.m_sipClient.writeInviteOk(invite, ByteBuffer.wrap(answer));
        offerAnswer.processOffer(offer, this.m_offerAnswerListener);
        
        m_log.debug("Done processing INVITE!!!");
        }

    public void visitRegister(final Register register)
        {
        // Should never happen on UAS.
        m_log.error("Got REGISTER request on UAS -- weird: "+register);
        }

    public void visitDoubleCrlfKeepAlive(final DoubleCrlfKeepAlive keepAlive)
        {
        // Should never happen on UAS.
        m_log.error("Got keep-alive request on UAS -- weird: ");
        }
    
    public void visitUnknownRequest(final UnknownSipRequest request)
        {
        m_log.error("Unknown request on UAS: "+request);
        }

    public void visitResponse(final SipResponse response)
        {
        // Identify the transaction for this response and the corresponding 
        // session it creates.
        m_log.debug("Visiting OK response: "+ response);
        notifyTransaction(response);
        }
    
    
    private void notifyTransaction(final SipMessage response)
        {
        final SipClientTransaction ct = 
            this.m_transactionTracker.getClientTransaction(response);
        m_log.debug("Accessed transaction: "+ct);
        
        if (ct == null)
            {
            m_log.warn("No matching transaction for response: "+response);
            return;
            }
        
        response.accept(ct);
        }
    }
