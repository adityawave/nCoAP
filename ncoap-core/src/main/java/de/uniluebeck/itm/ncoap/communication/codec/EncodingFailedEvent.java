/**
 * Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.uniluebeck.itm.ncoap.communication.codec;

import de.uniluebeck.itm.ncoap.application.client.Token;
import de.uniluebeck.itm.ncoap.application.client.MessageExchangeEvent;

import java.net.InetSocketAddress;

/**
 * An instance of {@link de.uniluebeck.itm.ncoap.communication.codec.EncodingFailedEvent} is sent upstream
 * whenever the encoding of an outgoing CoAP message failed.
 *
 * @author Oliver Kleine
 */
public class EncodingFailedEvent extends MessageExchangeEvent{

    private final Throwable cause;

    /**
     * @param remoteEndoint the remote endpoint which was the intended recipient of the message that caused this
     *                      event
     * @param messageID the message ID of the message that caused this event
     * @param token the {@link de.uniluebeck.itm.ncoap.application.client.Token} of the message that caused this
     *              event
     * @param cause the {@link java.lang.Throwable} that caused this event (the {@link Throwable#getMessage()} method
     *              is supposed to explain the reason)
     */
    EncodingFailedEvent(InetSocketAddress remoteEndoint, int messageID, Token token, Throwable cause){
        super(remoteEndoint, messageID, token, true);
        this.cause = cause;
    }

    /**
     * Returns the {@link java.lang.Throwable} that caused encoding to fail
     * @return the {@link java.lang.Throwable} that caused encoding to fail
     */
    public Throwable getCause() {
        return this.cause;
    }
}
