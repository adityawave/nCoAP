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
package de.uzl.itm.ncoap.communication.dispatching.client;

import com.google.common.collect.HashBasedTable;
import de.uzl.itm.ncoap.communication.AbstractCoapChannelHandler;
import de.uzl.itm.ncoap.communication.events.*;
import de.uzl.itm.ncoap.communication.events.client.LazyObservationTerminationEvent;
import de.uzl.itm.ncoap.message.CoapMessage;
import de.uzl.itm.ncoap.message.CoapRequest;
import de.uzl.itm.ncoap.message.CoapResponse;
import de.uzl.itm.ncoap.message.MessageCode;
import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>The {@link ClientCallbackManager} is responsible for
 * processing inbound {@link de.uzl.itm.ncoap.message.CoapResponse}s. That is why each
 * {@link de.uzl.itm.ncoap.message.CoapRequest} needs an associated instance of
 * {@link ClientCallback} to be called upon reception
 * of a related {@link de.uzl.itm.ncoap.message.CoapResponse}.</p>
 * <p/>
 * <p>Besides the response dispatching the
 * {@link ClientCallbackManager} also deals with
 * the reliability of inbound {@link de.uzl.itm.ncoap.message.CoapResponse}s, i.e. sends RST or ACK
 * messages if necessary.</p>
 *
 * @author Oliver Kleine
 */
public class ClientCallbackManager extends AbstractCoapChannelHandler implements RemoteSocketChangedEvent.Handler,
        EmptyAckReceivedEvent.Handler, ResetReceivedEvent.Handler, PartialContentReceivedEvent.Handler,
        MessageIDAssignedEvent.Handler, MessageRetransmittedEvent.Handler, TransmissionTimeoutEvent.Handler,
        MiscellaneousErrorEvent.Handler{

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private TokenFactory tokenFactory;

    private HashBasedTable<InetSocketAddress, Token, ClientCallback> clientCallbacks;
    private ReentrantReadWriteLock lock;


    /**
     * Creates a new instance of {@link ClientCallbackManager}
     *
     * @param executor     the {@link java.util.concurrent.ScheduledExecutorService} to execute the tasks, e.g. send,
     *                     receive and process {@link de.uzl.itm.ncoap.message.CoapMessage}s.
     * @param tokenFactory the {@link TokenFactory} to
     *                     provide {@link Token}
     *                     instances for outbound {@link de.uzl.itm.ncoap.message.CoapRequest}s
     */
    public ClientCallbackManager(ScheduledExecutorService executor, TokenFactory tokenFactory) {
        super(executor);
        this.clientCallbacks = HashBasedTable.create();
        this.lock = new ReentrantReadWriteLock();
        this.tokenFactory = tokenFactory;
    }


    @Override
    public boolean handleInboundCoapMessage(ChannelHandlerContext ctx, CoapMessage coapMessage, InetSocketAddress remoteSocket) {
        if(coapMessage instanceof CoapResponse) {
            handleInboundCoapResponse(ctx, (CoapResponse) coapMessage, remoteSocket);
            return false;
        } else {
            return true;
        }
    }


    @Override
    public boolean handleOutboundCoapMessage(ChannelHandlerContext ctx, CoapMessage coapMessage, InetSocketAddress remoteSocket) {
        return true;
    }


    @Override
    public void handleEvent(RemoteSocketChangedEvent event) {
        InetSocketAddress previousSocket = event.getPreviousRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = updateCallback(event.getRemoteSocket(), previousSocket, token);
        if(callback != null) {
            callback.processRemoteSocketChanged(event.getRemoteSocket(), previousSocket);
        } else {
            log.warn("No callback found for socket change (previous: \"{}\", token: {}", previousSocket, token);
        }
    }


    @Override
    public void handleEvent(EmptyAckReceivedEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = getCallback(remoteSocket, token);
        if(callback != null) {
            callback.processEmptyAcknowledgement();
        } else {
            log.warn("No callback found for empty ACK (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }


    @Override
    public void handleEvent(ResetReceivedEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = getCallback(remoteSocket, token);
        if(callback != null) {
            callback.processReset();
        } else {
            log.warn("No callback found for RESET (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }


    @Override
    public void handleEvent(PartialContentReceivedEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = getCallback(remoteSocket, token);
        if(callback != null) {
            callback.processReset();
        } else {
            log.warn("No callback found for RESET (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }


    @Override
    public void handleEvent(MessageIDAssignedEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = getCallback(remoteSocket, token);
        if(callback != null) {
            callback.processMessageIDAssignment(event.getMessageID());
        } else {
            log.warn("No callback found for MsgID assignment (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }


    @Override
    public void handleEvent(MessageRetransmittedEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = getCallback(remoteSocket, token);
        if(callback != null) {
            callback.processRetransmission();
        } else {
            log.warn("No callback found for retransmission (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }


    @Override
    public void handleEvent(TransmissionTimeoutEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = removeCallback(remoteSocket, token);
        if(callback != null) {
            callback.processTransmissionTimeout();
        } else {
            log.warn("No callback found for timeout (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }

    @Override
    public void handleEvent(MiscellaneousErrorEvent event) {
        InetSocketAddress remoteSocket = event.getRemoteSocket();
        Token token = event.getToken();
        ClientCallback callback = removeCallback(remoteSocket, token);
        if(callback != null) {
            callback.processMiscellaneousError(event.getDescription());
        } else {
            log.warn("No callback found for misc. error (remote socket: \"{}\", token: {}", remoteSocket, token);
        }
    }

    /**
     * This method is called by the {@link de.uzl.itm.ncoap.application.client.CoapClient} or by the
     * {@link de.uzl.itm.ncoap.application.endpoint.CoapEndpoint} to send a request to a remote endpoint (server).
     *
     * @param channel the {@link org.jboss.netty.channel.Channel} to send the message
     * @param coapRequest the {@link de.uzl.itm.ncoap.message.CoapRequest} to be sent
     * @param remoteSocket the {@link java.net.InetSocketAddress} of the recipient
     * @param callback the {@link de.uzl.itm.ncoap.communication.dispatching.client.ClientCallback} to be
     * called upon reception of a response or any kind of
     * {@link de.uzl.itm.ncoap.communication.events.AbstractMessageExchangeEvent}.
     */
    public void sendCoapRequest(Channel channel, CoapRequest coapRequest, InetSocketAddress remoteSocket,
                                ClientCallback callback) {
        getExecutor().submit(new WriteCoapMessageTask(channel, coapRequest, remoteSocket, callback));
    }


    public void sendCoapPing(Channel channel, InetSocketAddress remoteSocket, ClientCallback callback) {
        final CoapMessage coapPing = CoapMessage.createPing(CoapMessage.UNDEFINED_MESSAGE_ID);
        getExecutor().submit(new WriteCoapMessageTask(channel, coapPing, remoteSocket, callback));
    }


    private ClientCallback updateCallback(InetSocketAddress remoteSocket, InetSocketAddress previousRemoteSocket, Token token) {
        try {
            this.lock.readLock().lock();
            if(getCallback(previousRemoteSocket, token) == null) {
                return null;
            }
        } finally {
            this.lock.readLock().unlock();
        }

        try {
            this.lock.writeLock().lock();
            ClientCallback callback = this.clientCallbacks.remove(remoteSocket, token);
            if(callback != null) {
                this.clientCallbacks.put(remoteSocket, token, callback);
                log.info("Updated remote socket (old: \"{}\", new: \"{}\")", previousRemoteSocket, remoteSocket);
            }
            return callback;
        } finally {
            this.lock.writeLock().unlock();
        }
    }


    private void addCallback(InetSocketAddress remoteEndpoint, Token token, ClientCallback clientCallback) {
        try {
            this.lock.readLock().lock();
            if (this.clientCallbacks.contains(remoteEndpoint, token)) {
                log.error("Tried to use token twice (remote endpoint: {}, token: {})", remoteEndpoint, token);
                return;
            }
        } finally {
            this.lock.readLock().unlock();
        }

        try {
            this.lock.writeLock().lock();
            if (this.clientCallbacks.contains(remoteEndpoint, token)) {
                log.error("Tried to use token twice (remote endpoint: {}, token: {})", remoteEndpoint, token);
            } else {
                clientCallbacks.put(remoteEndpoint, token, clientCallback);
                log.debug("Added callback (remote endpoint: {}, token: {})", remoteEndpoint, token);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }


    private ClientCallback removeCallback(InetSocketAddress remoteEndpoint, Token token) {
        try {
            this.lock.readLock().lock();
            if (!this.clientCallbacks.contains(remoteEndpoint, token)) {
                log.warn("No callback found to be removed (remote endpoint: {}, token: {})", remoteEndpoint, token);
                return null;
            }
        } finally {
            this.lock.readLock().unlock();
        }

        try {
            this.lock.writeLock().lock();
            ClientCallback callback = clientCallbacks.remove(remoteEndpoint, token);
            if (callback == null) {
                log.warn("No callback found to be removed (remote endpoint: {}, token: {})", remoteEndpoint, token);
            } else {
                log.info("Removed callback (remote endpoint: {}, token: {}). Remaining: {}",
                        new Object[]{remoteEndpoint, token, this.clientCallbacks.size()});
                this.tokenFactory.passBackToken(token);
            }
            return callback;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private ClientCallback getCallback(InetSocketAddress remoteAddress, Token token) {
        try {
            this.lock.readLock().lock();
            return this.clientCallbacks.get(remoteAddress, token);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private void handleInboundCoapResponse(ChannelHandlerContext ctx, CoapResponse coapResponse, InetSocketAddress remoteSocket) {
        Token token = coapResponse.getToken();
        ClientCallback callback = getCallback(remoteSocket, token);

        if (callback != null) {
            // observation callback found
            if (coapResponse.isErrorResponse() || !coapResponse.isUpdateNotification()) {
                if (log.isInfoEnabled()) {
                    if (MessageCode.isErrorMessage(coapResponse.getMessageCode())) {
                        log.info("Observation callback removed because of error response!");
                    } else {
                        log.info("Observation callback removed because inbound response was no update notification!");
                    }
                }
                removeCallback(remoteSocket, token);
            } else if (!callback.continueObservation()) {
                // send internal message to stop the observation
                triggerEvent(ctx.getChannel(), new LazyObservationTerminationEvent(remoteSocket, token));
            }

            //Process the CoAP response
            log.debug("Callback found for token {} from {}.", token, remoteSocket);
            callback.processCoapResponse(coapResponse);
        } else {
            log.warn("No callback found for CoAP response (from {}): {}", remoteSocket, coapResponse);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent ee) {
        log.error("Exception: ", ee.getCause());
    }


    private class WriteCoapMessageTask implements Runnable {

        private final Channel channel;
        private final CoapMessage coapMessage;
        private final InetSocketAddress remoteSocket;
        private final ClientCallback callback;

        public WriteCoapMessageTask(Channel channel, CoapMessage coapMessage, InetSocketAddress remoteSocket,
                                    ClientCallback callback) {

            this.channel = channel;
            this.coapMessage = coapMessage;
            this.remoteSocket = remoteSocket;
            this.callback = callback;
        }

        @Override
        public void run() {
            if (this.coapMessage.isPingMessage()) {
                //CoAP ping
                Token emptyToken = new Token(new byte[0]);
                if (getCallback(remoteSocket, emptyToken) != null) {
                    String description = "There is another ongoing PING for \"" + remoteSocket + "\".";
                    callback.processMiscellaneousError(description);
                    return;
                } else {
                    // no other PING for the same remote socket...
                    this.coapMessage.setToken(emptyToken);
                }
            } else if (this.coapMessage.isRequest() && this.coapMessage.getObserve() == 1) {
                // request to stop an ongoing observation
                Token token = this.coapMessage.getToken();
                if (getCallback(this.remoteSocket, token) == null) {
                    String description = "No ongoing observation on remote endpoint " + remoteSocket
                            + " and token " + token + "!";
                    this.callback.processMiscellaneousError(description);
                    return;
                }
            } else {
                //Prepare CoAP request, the response reception and then send the CoAP request
                Token token = tokenFactory.getNextToken();
                if (token == null) {
                    String description = "No token available for remote endpoint " + remoteSocket + ".";
                    this.callback.processMiscellaneousError(description);
                    return;
                } else {
                    this.coapMessage.setToken(token);
                }
            }

            //Add the response callback to wait for the inbound response
            addCallback(this.remoteSocket, this.coapMessage.getToken(), this.callback);
            sendRequest();
        }

        private void sendRequest() {
            ChannelFuture future = Channels.write(this.channel, this.coapMessage, this.remoteSocket);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        ClientCallback callback = removeCallback(remoteSocket, coapMessage.getToken());
                        log.error("Could not write CoAP Request!", future.getCause());
                        if(callback != null) {
                            callback.processMiscellaneousError("Message could not be sent (Reason: \"" +
                                    future.getCause().getMessage() + ")\"");
                        }
                    }
                }
            });
        }
    }
}
