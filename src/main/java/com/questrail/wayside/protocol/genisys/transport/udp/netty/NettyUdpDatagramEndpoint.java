package com.questrail.wayside.protocol.genisys.transport.udp.netty;

import com.questrail.wayside.protocol.genisys.transport.DatagramEndpoint;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpointListener;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * NettyUdpDatagramEndpoint
 * =============================================================================
 * Netty-backed implementation of the GENISYS {@link DatagramEndpoint} port.
 *
 * <h2>Architectural Role</h2>
 * This class is a <strong>pure transport adapter</strong>.
 *
 * It MUST NOT:
 * <ul>
 *   <li>Decode GENISYS frames</li>
 *   <li>Interpret protocol semantics</li>
 *   <li>Emit {@code GenisysEvent} instances</li>
 *   <li>Schedule retries, polls, or timeouts</li>
 * </ul>
 *
 * <h2>Netty containment rule</h2>
 * Netty types (e.g., {@code Channel}, {@code EventLoopGroup}, {@code ByteBuf})
 * MUST NOT escape this package.
 *
 * <p>Inbound payloads are copied into {@code byte[]} and emitted to the port
 * listener. All reference-counted buffers are released internally.</p>
 *
 * <h2>Lifecycle</h2>
 * - {@link #start()} binds the UDP socket and begins receiving datagrams.
 * - {@link #stop()} closes the channel and shuts down the event loop group.
 */
public final class NettyUdpDatagramEndpoint implements DatagramEndpoint
{
    private final InetSocketAddress bindAddress;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;

    private volatile DatagramEndpointListener listener;
    private volatile Channel channel;

    /**
     * Construct a Netty UDP endpoint binding to the specified local address.
     *
     * <p>We use a dedicated {@link NioEventLoopGroup} by default to keep the
     * transport adapter self-contained. If a shared group is desired, introduce
     * that as a Phase 4 implementation policy in the composition root, not here.</p>
     */
    public NettyUdpDatagramEndpoint(InetSocketAddress bindAddress)
    {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");

        this.group = new NioEventLoopGroup(1);
        this.bootstrap = new Bootstrap();

        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch)
                    {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new InboundHandler());
                    }
                });
    }

    @Override
    public void setListener(DatagramEndpointListener listener)
    {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public void start()
    {
        DatagramEndpointListener l = requireListener();

        // Bind asynchronously; notify listener on success/failure.
        ChannelFuture f = bootstrap.bind(bindAddress);
        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                l.onTransportUp();
            }
            else {
                l.onTransportDown(future.cause());
            }
        });
    }

    @Override
    public void stop()
    {
        DatagramEndpointListener l = listener;

        Channel ch = channel;
        if (ch != null) {
            ch.close();
        }

        // Shut down the event loop group.
        group.shutdownGracefully();

        if (l != null) {
            l.onTransportDown(null);
        }
    }

    @Override
    public void send(SocketAddress remote, byte[] payload)
    {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(payload, "payload");

        Channel ch = channel;
        if (ch == null) {
            // Transport not up yet. Caller (executor/wiring) decides how to
            // handle this; we do not invent retries here.
            return;
        }

        ByteBuf buf = Unpooled.wrappedBuffer(payload);
        DatagramPacket pkt = new DatagramPacket(buf, (InetSocketAddress) remote);
        ch.writeAndFlush(pkt);
    }

    private DatagramEndpointListener requireListener()
    {
        DatagramEndpointListener l = listener;
        if (l == null) {
            throw new IllegalStateException("DatagramEndpointListener must be set before start()");
        }
        return l;
    }

    /**
     * InboundHandler
     * -------------------------------------------------------------------------
     * Receives Netty {@link DatagramPacket}s and forwards raw payload bytes
     * to the port listener.
     */
    private final class InboundHandler extends SimpleChannelInboundHandler<DatagramPacket>
    {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet)
        {
            DatagramEndpointListener l = listener;
            if (l == null) {
                return;
            }

            // Copy the payload into a plain byte[] (Netty containment rule).
            ByteBuf content = packet.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);

            SocketAddress remote = packet.sender();
            l.onDatagram(remote, bytes);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx)
        {
            DatagramEndpointListener l = listener;
            if (l != null) {
                l.onTransportDown(null);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
        {
            DatagramEndpointListener l = listener;
            if (l != null) {
                l.onTransportDown(cause);
            }
            ctx.close();
        }
    }
}
