/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.net.server;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.scoring.PeerScoringManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.eth.message.Eth62MessageFactory;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.p2p.P2pMessageFactory;
import org.ethereum.net.rlpx.InitiatorHandshakeInitPacketHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class InitiatorHandshakeInitializer extends ChannelInitializer<NioSocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger("net");

    private final String remoteId;
    private final RskSystemProperties config;
    private final ChannelManager channelManager;
    private final CompositeEthereumListener ethereumListener;
    private final ConfigCapabilities configCapabilities;
    private final NodeManager nodeManager;
    private final RskWireProtocol.Factory rskWireProtocolFactory;
    private final Eth62MessageFactory eth62MessageFactory;
    private final StaticMessages staticMessages;
    private final PeerScoringManager peerScoringManager;

    public InitiatorHandshakeInitializer(
            String remoteId,
            RskSystemProperties config,
            ChannelManager channelManager,
            CompositeEthereumListener ethereumListener,
            ConfigCapabilities configCapabilities,
            NodeManager nodeManager,
            RskWireProtocol.Factory rskWireProtocolFactory,
            Eth62MessageFactory eth62MessageFactory,
            StaticMessages staticMessages,
            PeerScoringManager peerScoringManager) {
        this.remoteId = remoteId;
        this.config = config;
        this.channelManager = channelManager;
        this.ethereumListener = ethereumListener;
        this.configCapabilities = configCapabilities;
        this.nodeManager = nodeManager;
        this.rskWireProtocolFactory = rskWireProtocolFactory;
        this.eth62MessageFactory = eth62MessageFactory;
        this.staticMessages = staticMessages;
        this.peerScoringManager = peerScoringManager;
    }

    @Override
    public void initChannel(NioSocketChannel ch) {
        try {
            logger.info("Open outbound connection, channel: {}", ch);

            MessageQueue messageQueue = new MessageQueue();
            P2pHandler p2pHandler = new P2pHandler(ethereumListener, messageQueue, config.getPeerP2PPingInterval());
            MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
            Channel channel = new Channel(messageQueue, messageCodec, nodeManager, rskWireProtocolFactory, eth62MessageFactory, staticMessages, remoteId);
            InitiatorHandshakeInitPacketHandler handshakeHandler = new InitiatorHandshakeInitPacketHandler(config, peerScoringManager, p2pHandler, messageCodec, configCapabilities, remoteId, channel);

            ch.pipeline().addLast("readTimeoutHandler", new ReadTimeoutHandler(config.peerChannelReadTimeout(), TimeUnit.SECONDS));
            ch.pipeline().addLast("handshakeHandler", handshakeHandler);

            messageCodec.setChannel(channel);
            messageQueue.setChannel(channel);
            messageCodec.setP2pMessageFactory(new P2pMessageFactory());

            channelManager.add(channel);

            // limit the size of receiving buffer to 1024
            SocketChannelConfig channelConfig = ch.config();
            channelConfig.setRecvByteBufAllocator(new FixedRecvByteBufAllocator(16_777_216));
            channelConfig.setOption(ChannelOption.SO_RCVBUF, 16_777_216);
            channelConfig.setOption(ChannelOption.SO_BACKLOG, 1024);

            // be aware of channel closing
            ch.closeFuture().addListener(future -> channelManager.notifyDisconnect(channel));

        } catch (Exception e) {
            logger.error("Unexpected error: ", e);
        }
    }
}
