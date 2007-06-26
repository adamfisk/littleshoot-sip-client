package org.lastbamboo.common.sip.client;

import java.io.IOException;
import java.net.URI;

import org.apache.commons.id.uuid.UUID;
import org.apache.mina.common.ByteBuffer;
import org.lastbamboo.common.sip.stack.message.Invite;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionListener;

/**
 * Interface for an individual SIP client connected to an individual SIP proxy.
 */
public interface SipClient
    {

    /**
     * Sends an INVITE request to the host identified by the specified SIP URI.
     * 
     * @param sipUri The URI of the SIP host to send the INVITE request to.
     * @param body The body to encapsulate in the INVITE, such as SDP data.
     * @param listener The listener for transaction state changes.
     * @throws IOException If there's a transport error sending the INVITE.
     */
    void invite(URI sipUri, byte[] body, SipTransactionListener listener) 
        throws IOException;

    /**
     * Accessor for the unique instance ID for this client.
     * 
     * @return The instance ID for this client.
     */
    UUID getInstanceId();

    /**
     * Accessor the contact URI for the client.
     * 
     * @return The contact URI for the client.
     */
    URI getContactUri();

    /**
     * Accessor for the SIP URI for this client.
     * 
     * @return The SIP URI for this client.
     */
    URI getSipUri();

    /**
     * Accessor for the URI of the SIP proxy the client is connected to and
     * registered with.
     * 
     * @return The URI of the SIP proxy the client is connected to and
     * registered with.
     */
    URI getProxyUri();

    /**
     * Sends a CRLF keep-alive message, as specified in the SIP outbound
     * draft at:
     * 
     * http://www.ietf.org/internet-drafts/draft-ietf-sip-outbound-08.txt
     */
    void writeCrlfKeepAlive();

    /**
     * Sends an INVITE OK message.
     * 
     * @param invite The INVITE we're responding to.
     * @param body The body of the INVITE OK.
     */
    void writeInviteOk(Invite invite, ByteBuffer body);

    /**
     * Registers the SIP client.
     * @throws IOException If we do not get a successful registration response.
     */
    void register() throws IOException;

    /**
     * Connects to the proxy server.
     * 
     * @throws IOException If we cannot connect.
     */
    void connect() throws IOException;

    }
