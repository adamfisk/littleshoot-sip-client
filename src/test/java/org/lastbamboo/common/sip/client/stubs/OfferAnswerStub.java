package org.lastbamboo.common.sip.client.stubs;

import java.io.IOException;

import org.littleshoot.mina.common.ByteBuffer;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;

public class OfferAnswerStub implements OfferAnswer
    {

    private byte[] m_answer = new byte[0];

    public OfferAnswerStub(byte[] answer)
        {
        m_answer = answer;
        }

    public OfferAnswerStub()
        {
        // TODO Auto-generated constructor stub
        }

    public byte[] generateAnswer()
        {
        return this.m_answer;
        }

    public byte[] generateOffer()
        {
        // TODO Auto-generated method stub
        return null;
        }

    public void processOffer(ByteBuffer offer) throws IOException
        {
        // TODO Auto-generated method stub
        
        }

    public void processAnswer(ByteBuffer answer) throws IOException
        {
        // TODO Auto-generated method stub
        
        }

    public void processAnswer(ByteBuffer answer, OfferAnswerListener offerAnswerListener)
        {
        // TODO Auto-generated method stub
        
        }

    public void processOffer(ByteBuffer offer, OfferAnswerListener offerAnswerListener)
        {
        // TODO Auto-generated method stub
        
        }

    }
