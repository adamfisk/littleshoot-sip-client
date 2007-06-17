package org.lastbamboo.common.sip.client;

import org.apache.mina.common.IoSession;
import org.lastbamboo.common.offer.OfferProcessor;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitor;
import org.lastbamboo.common.sip.stack.message.SipMessageVisitorFactory;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;

/**
 * Factory for creating SIP message visitors for SIP clients.
 */
public class SipClientMessageVisitorFactory implements SipMessageVisitorFactory
    {

    private final SipTransactionTracker m_transactionTracker;
    private final OfferProcessor m_offerProcessor;
    private final SipClient m_sipClient;

    /**
     * Creates a new message visitor factory.
     * 
     * @param sipClient The SIP client.
     * @param transactionTracker The tracker for SIP transactions.
     * @param inviteProcessor The INVITE processing class.
     */
    public SipClientMessageVisitorFactory(
        final SipClient sipClient, 
        final SipTransactionTracker transactionTracker,
        final OfferProcessor inviteProcessor)
        {
        m_sipClient = sipClient;
        m_transactionTracker = transactionTracker;
        m_offerProcessor = inviteProcessor;
        }

    public SipMessageVisitor createVisitor(final IoSession session)
        {
        return new SipClientMessageVisitor(this.m_sipClient,
            this.m_transactionTracker, this.m_offerProcessor);
        }

    }
