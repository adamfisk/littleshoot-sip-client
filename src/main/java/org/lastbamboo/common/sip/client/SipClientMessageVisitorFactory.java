package org.lastbamboo.common.sip.client;

import org.apache.mina.common.IoSession;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitor;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitorFactory;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;

/**
 * Factory for creating SIP message visitors for SIP clients.
 * 
 * TODO: Ideally this wouldn't be tied to actual sockets in any way.
 */
public class SipClientMessageVisitorFactory implements SipMessageVisitorFactory
    {

    private final SipTransactionTracker m_transactionTracker;
    private final SipClient m_sipClient;
    private final OfferAnswerFactory m_offerAnswerFactory;
    private final OfferAnswerListener m_offerAnswerListener;

    /**
     * Creates a new message visitor factory.
     * 
     * @param sipClient The SIP client.
     * @param transactionTracker The tracker for SIP transactions.
     * @param offerAnswerFactory The factory for creating offer/answer 
     * instances.
     * @param offerAnswerListener Listener for offer/answer events.
     */
    public SipClientMessageVisitorFactory(
        final SipClient sipClient, 
        final SipTransactionTracker transactionTracker,
        final OfferAnswerFactory offerAnswerFactory,
        final OfferAnswerListener offerAnswerListener)
        {
        m_sipClient = sipClient;
        m_transactionTracker = transactionTracker;
        m_offerAnswerFactory = offerAnswerFactory;
        m_offerAnswerListener = offerAnswerListener;
        }

    public SipMessageVisitor createVisitor(final IoSession session)
        {
        return new SipClientMessageVisitor(this.m_sipClient,
            this.m_transactionTracker, this.m_offerAnswerFactory, 
            this.m_offerAnswerListener);
        }

    }
