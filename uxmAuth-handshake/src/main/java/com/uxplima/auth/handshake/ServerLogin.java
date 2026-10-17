package com.uxplima.auth.handshake;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jspecify.annotations.Nullable;

/**
 * A login as the server's own listener holds it.
 *
 * <p>Asking for the key does what an online server does at its hello: the listener takes the name and waits
 * for the key, and the encryption request carries the server's own key and the listener's own challenge. From
 * there the server's own code takes over: it checks the challenge, switches the connection to AES, asks
 * Mojang's {@code hasJoined}, and fires the pre-login with the profile Mojang answered, skin and all. Nothing
 * of the cryptography is written twice.
 *
 * <p>The challenge is the one private member this reads, by name, once. A server without it is one this
 * module was not built for, and {@link Handshake#whyNot()} says so before anything is installed.
 */
final class ServerLogin implements Login {

    /** The listener's verify token, or null on a server whose login has none by that name. */
    static final @Nullable Field CHALLENGE = challengeField();

    private final Connection connection;
    private final ServerLoginPacketListenerImpl listener;
    private final InetSocketAddress address;

    private ServerLogin(Connection connection, ServerLoginPacketListenerImpl listener, InetSocketAddress address) {
        this.connection = connection;
        this.listener = listener;
        this.address = address;
    }

    /** The login on this channel, while it is in its login phase over the network. */
    static Optional<Login> of(Channel channel) {
        if (!(channel.pipeline().get(HandlerNames.PACKET_HANDLER) instanceof Connection connection)
                || !(connection.getPacketListener() instanceof ServerLoginPacketListenerImpl listener)
                || !(connection.getRemoteAddress() instanceof InetSocketAddress address)
                || CHALLENGE == null) {
            return Optional.empty();
        }
        return Optional.of(new ServerLogin(connection, listener, address));
    }

    @Override
    public InetSocketAddress address() {
        return address;
    }

    @Override
    public String host() {
        return Objects.requireNonNullElse(connection.hostname, "");
    }

    @Override
    public void askForKey(ServerboundHelloPacket hello) {
        listener.requestedUsername = hello.name();
        listener.requestedUuid = hello.profileId();
        listener.state = ServerLoginPacketListenerImpl.State.KEY;
        byte[] key = MinecraftServer.getServer().getKeyPair().getPublic().getEncoded();
        connection.send(new ClientboundHelloPacket("", key, challenge(), true));
    }

    /** The server sets this state only after it checked the key, and holds it while Mojang's answer is read. */
    @Override
    public boolean proved() {
        return listener.state == ServerLoginPacketListenerImpl.State.AUTHENTICATING;
    }

    private byte[] challenge() {
        try {
            return (byte[])
                    Objects.requireNonNull(Objects.requireNonNull(CHALLENGE).get(listener), "challenge");
        } catch (IllegalAccessException refused) {
            throw new IllegalStateException("The login's challenge cannot be read", refused);
        }
    }

    private static @Nullable Field challengeField() {
        try {
            Field field = ServerLoginPacketListenerImpl.class.getDeclaredField("challenge");
            if (field.getType() != byte[].class) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException missing) {
            return null;
        }
    }
}
