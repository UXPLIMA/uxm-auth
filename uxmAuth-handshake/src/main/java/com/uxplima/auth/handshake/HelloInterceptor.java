package com.uxplima.auth.handshake;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.Refusal;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.jspecify.annotations.Nullable;

/**
 * Holds the first hello of a connection until the door decides, then asks the client for its key or hands
 * the hello to the server as it came.
 *
 * <p>The door may wait on the database and the web, so the answer comes on another thread and is carried back
 * to the connection's own event loop before anything touches the channel. A connection gone by then is
 * dropped. A client sends nothing while its hello waits, so anything that arrives meanwhile, a second hello
 * above all, closes the connection: a second hello passed on would reach the server unchecked while the first
 * one was still being decided. A door that fails refuses the connection at its pre-login, never lets it in
 * unchecked.
 */
final class HelloInterceptor extends ChannelInboundHandlerAdapter {

    private final HelloDoor door;
    private final HandshakeResults results;
    private final Function<Channel, Optional<Login>> logins;
    private final Logger logger;
    private boolean seen;
    private boolean waiting;
    private @Nullable Login login;

    HelloInterceptor(
            HelloDoor door, HandshakeResults results, Function<Channel, Optional<Login>> logins, Logger logger) {
        this.door = Objects.requireNonNull(door, "door");
        this.results = Objects.requireNonNull(results, "results");
        this.logins = Objects.requireNonNull(logins, "logins");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (waiting) {
            ReferenceCountUtil.release(msg);
            ChannelFuture unused = ctx.close();
            return;
        }
        if (seen || !(msg instanceof ServerboundHelloPacket hello)) {
            ctx.fireChannelRead(msg);
            return;
        }
        seen = true;
        Optional<Login> found = logins.apply(ctx.channel());
        if (found.isEmpty()) {
            ctx.fireChannelRead(msg);
            return;
        }
        Login mine = found.get();
        login = mine;
        waiting = true;
        CompletableFuture<Decision> unused = door.decide(hello.name(), mine.address(), mine.host())
                .whenComplete((decision, failure) ->
                        ctx.executor().execute(() -> answer(ctx, mine, hello, decision, failure)));
    }

    private void answer(
            ChannelHandlerContext ctx,
            Login mine,
            ServerboundHelloPacket hello,
            @Nullable Decision decision,
            @Nullable Throwable failure) {
        waiting = false;
        if (!ctx.channel().isActive()) {
            return;
        }
        if (failure != null || decision == null) {
            logger.log(
                    Level.WARNING, "Could not decide whether " + hello.name() + " is asked for the handshake", failure);
            results.let(mine.address(), new Decision.Refuse(Refusal.LOOKUP_FAILED));
            ctx.fireChannelRead(hello);
            return;
        }
        if (decision instanceof Decision.Handshake) {
            results.asked(mine, hello.name());
            if (!mine.askForKey(hello)) {
                results.closed(mine.address());
                ChannelFuture unused = ctx.close();
            }
            return;
        }
        results.let(mine.address(), decision);
        ctx.fireChannelRead(hello);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Login mine = login;
        if (mine != null) {
            results.closed(mine.address()).ifPresent(name -> door.failed(mine.address(), name));
        }
        super.channelInactive(ctx);
    }
}
