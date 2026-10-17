package com.uxplima.auth.handshake;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.Refusal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The hello is held while the door decides, and answered with a key request or passed to the server. */
final class HelloInterceptorTest {

    private static final InetSocketAddress FROM = new InetSocketAddress(InetAddress.getLoopbackAddress(), 50123);
    private static final ServerboundHelloPacket HELLO = new ServerboundHelloPacket("Notch", UUID.randomUUID());

    /** A login as the server's listener would be, recording what the interceptor did to it. */
    private static final class FakeLogin implements Login {
        final List<String> done = new ArrayList<>();
        boolean proved;
        boolean atHello = true;
        boolean bedrock;

        @Override
        public InetSocketAddress address() {
            return FROM;
        }

        @Override
        public String host() {
            return "play.example.org";
        }

        @Override
        public boolean askForKey(ServerboundHelloPacket hello) {
            if (!atHello) {
                return false;
            }
            done.add("key:" + hello.name());
            return true;
        }

        @Override
        public boolean proved() {
            return proved;
        }

        @Override
        public boolean bedrock() {
            return bedrock;
        }
    }

    /** A door that answers when the test says so, and remembers the failures it was told of. */
    private static final class FakeDoor implements HelloDoor {
        final CompletableFuture<Decision> answer = new CompletableFuture<>();
        final List<String> asked = new ArrayList<>();
        final List<String> failed = new ArrayList<>();

        @Override
        public CompletableFuture<Decision> decide(String name, InetSocketAddress address, String host) {
            asked.add(name + "@" + address.getPort() + "/" + host);
            return answer;
        }

        @Override
        public void failed(InetSocketAddress address, String name) {
            failed.add(name + "@" + address.getPort());
        }
    }

    /** What reached the server's own handler behind the interceptor. */
    private static final class Server extends ChannelInboundHandlerAdapter {
        final List<Object> read = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            read.add(msg);
        }
    }

    private final FakeLogin login = new FakeLogin();
    private final FakeDoor door = new FakeDoor();
    private final HandshakeResults results = new HandshakeResults();
    private final Server server = new Server();
    private final EmbeddedChannel channel = new EmbeddedChannel(
            new HelloInterceptor(door, results, ignored -> Optional.of(login), Logger.getLogger("uxmauth-test")),
            server);

    @Test
    @DisplayName("the hello waits for the door, and the server does not see it meanwhile")
    void theHelloWaitsForTheDoor() {
        channel.writeInbound(HELLO);

        assertThat(door.asked).containsExactly("Notch@50123/play.example.org");
        assertThat(server.read).isEmpty();
    }

    @Test
    @DisplayName("a hello the door asks for the handshake is answered with a key request and never reaches the server")
    void aHandshakeIsAskedForTheKey() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();

        assertThat(login.done).containsExactly("key:Notch");
        assertThat(server.read).isEmpty();
    }

    @Test
    @DisplayName("the pre-login reads proved only when the server's own check of the key passed")
    void thePreLoginReadsWhetherTheKeyWasProved() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();

        login.proved = true;
        assertThat(results.take(FROM)).contains(new HelloOutcome.Proved());
        assertThat(results.take(FROM)).isEmpty();
    }

    @Test
    @DisplayName("a pre-login on a login that was asked and not proved reads not proved")
    void anUnprovedLoginReadsNotProved() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();

        assertThat(results.take(FROM)).contains(new HelloOutcome.NotProved());
    }

    @Test
    @DisplayName("a hello the door lets in with a password reaches the server unchanged, and the decision waits")
    void aCrackedHelloReachesTheServer() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Cracked(true, false));
        channel.runPendingTasks();

        assertThat(server.read).containsExactly(HELLO);
        assertThat(login.done).isEmpty();
        assertThat(results.take(FROM)).contains(new HelloOutcome.Decided(new Decision.Cracked(true, false)));
    }

    @Test
    @DisplayName("a refusal reaches the server too, so the pre-login turns it away with the message")
    void aRefusalWaitsForThePreLogin() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Refuse(Refusal.LOOKUP_FAILED));
        channel.runPendingTasks();

        assertThat(server.read).containsExactly(HELLO);
        assertThat(results.take(FROM)).contains(new HelloOutcome.Decided(new Decision.Refuse(Refusal.LOOKUP_FAILED)));
    }

    @Test
    @DisplayName("a connection that closes while asked for the key, before any pre-login, failed the handshake")
    void aCloseWhileAskedIsAFailure() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();

        channel.finishAndReleaseAll();

        assertThat(door.failed).containsExactly("Notch@50123");
        assertThat(results.size()).isZero();
    }

    @Test
    @DisplayName("a connection that reached the pre-login and closes later did not fail")
    void aCloseAfterThePreLoginIsNoFailure() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();
        login.proved = true;
        results.take(FROM);

        channel.finishAndReleaseAll();

        assertThat(door.failed).isEmpty();
    }

    @Test
    @DisplayName("a decision nobody took is let go when its connection closes")
    void anUntakenDecisionIsLetGo() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Cracked(false, false));
        channel.runPendingTasks();

        channel.finishAndReleaseAll();

        assertThat(results.size()).isZero();
        assertThat(door.failed).isEmpty();
    }

    @Test
    @DisplayName("a door that fails turns the connection away at its pre-login, never through unchecked")
    void aFailingDoorRefusesAtThePreLogin() {
        channel.writeInbound(HELLO);
        door.answer.completeExceptionally(new IllegalStateException("database down"));
        channel.runPendingTasks();

        assertThat(server.read).containsExactly(HELLO);
        assertThat(results.take(FROM)).contains(new HelloOutcome.Decided(new Decision.Refuse(Refusal.LOOKUP_FAILED)));
    }

    @Test
    @DisplayName("a second hello while the first one waits closes the connection, and the server sees neither")
    void aSecondHelloWhileWaitingCloses() {
        channel.writeInbound(HELLO);

        channel.writeInbound(new ServerboundHelloPacket("Notch", UUID.randomUUID()));

        assertThat(channel.isActive()).isFalse();
        assertThat(server.read).isEmpty();
    }

    @Test
    @DisplayName("anything at all while the hello waits closes the connection, since a client sends nothing then")
    void anythingWhileWaitingCloses() {
        channel.writeInbound(HELLO);

        channel.writeInbound(new Object());

        assertThat(channel.isActive()).isFalse();
        assertThat(server.read).isEmpty();
    }

    @Test
    @DisplayName("a login no longer at its hello is closed rather than switched to the key")
    void aLoginPastItsHelloIsClosed() {
        channel.writeInbound(HELLO);
        login.atHello = false;
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();

        assertThat(channel.isActive()).isFalse();
        assertThat(server.read).isEmpty();
        assertThat(results.size()).isZero();
    }

    @Test
    @DisplayName("a connection that closed before the door answered is dropped, and nothing is kept for it")
    void aConnectionGoneBeforeTheAnswerIsDropped() {
        channel.writeInbound(HELLO);
        channel.finishAndReleaseAll();
        door.answer.complete(new Decision.Handshake());
        channel.runPendingTasks();

        assertThat(login.done).isEmpty();
        assertThat(results.size()).isZero();
        assertThat(door.failed).isEmpty();
    }

    @Test
    @DisplayName("a Bedrock connection Floodgate vouched for passes straight to the server, never asked for a key")
    void aBedrockConnectionPassesStraightThrough() {
        login.bedrock = true;

        channel.writeInbound(HELLO);

        assertThat(server.read).containsExactly(HELLO);
        assertThat(door.asked).isEmpty();
        assertThat(results.size()).isZero();
    }

    @Test
    @DisplayName("only the first hello is held; anything after it passes straight through")
    void onlyTheFirstHelloIsHeld() {
        channel.writeInbound(HELLO);
        door.answer.complete(new Decision.Cracked(false, false));
        channel.runPendingTasks();
        Object other = new Object();

        channel.writeInbound(HELLO, other);

        assertThat(server.read).containsExactly(HELLO, HELLO, other);
        assertThat(door.asked).hasSize(1);
    }
}
