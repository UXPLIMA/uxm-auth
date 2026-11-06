package com.uxplima.auth.handshake;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.uxplima.auth.domain.premium.Decision;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.junit.jupiter.api.Test;

class HelloInterceptorTest {

    private static final InetSocketAddress ADDRESS = new InetSocketAddress("203.0.113.7", 50000);

    private final HandshakeResults results = new HandshakeResults();

    /** A login the door lets through with a password, or one Floodgate marked a Bedrock one. */
    private record JavaLogin(boolean bedrock) implements Login {
        @Override
        public InetSocketAddress address() {
            return ADDRESS;
        }

        @Override
        public String host() {
            return "play.example.org";
        }

        @Override
        public boolean askForKey(ServerboundHelloPacket hello) {
            return true;
        }

        @Override
        public boolean proved() {
            return false;
        }
    }

    private static final class Door implements HelloDoor {
        @Override
        public CompletableFuture<Decision> decide(String name, InetSocketAddress address, String host) {
            return CompletableFuture.completedFuture(new Decision.Cracked(false, false));
        }

        @Override
        public void failed(InetSocketAddress address, String name) {}
    }

    private EmbeddedChannel channel(boolean bedrock) {
        return new EmbeddedChannel(new HelloInterceptor(
                new Door(),
                results,
                unused -> Optional.of(new JavaLogin(bedrock)),
                Logger.getLogger(HelloInterceptorTest.class.getName())));
    }

    @Test
    void aSecondHelloAfterTheDoorLetTheFirstThroughClosesTheConnection() {
        EmbeddedChannel channel = channel(false);

        channel.writeInbound(new ServerboundHelloPacket("Steve", UUID.randomUUID()));
        channel.runPendingTasks();
        Object first = channel.readInbound();
        channel.writeInbound(new ServerboundHelloPacket("Notch", UUID.randomUUID()));
        channel.runPendingTasks();

        assertThat(first).isInstanceOf(ServerboundHelloPacket.class);
        assertThat(channel.<Object>readInbound()).isNull();
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void aBedrockHelloIsMarkedForItsPreLoginAndASecondOneClosesTheConnection() {
        EmbeddedChannel channel = channel(true);

        channel.writeInbound(new ServerboundHelloPacket("Steve", UUID.randomUUID()));
        boolean marked = results.takeBedrock(ADDRESS);
        channel.writeInbound(new ServerboundHelloPacket("Notch", UUID.randomUUID()));

        assertThat(marked).isTrue();
        assertThat(channel.isOpen()).isFalse();
    }
}
