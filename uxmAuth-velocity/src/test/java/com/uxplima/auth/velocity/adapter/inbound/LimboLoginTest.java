package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** In the proxy's waiting world a player types /login or /register, and the same use cases answer. */
final class LimboLoginTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final PlayerName NAME = new PlayerName("Steve");
    private static final Audience READER = Audience.empty();
    private static final Claim.Waiting NONE = new Claim.Waiting(false, false, false);

    private final ProxyWorld world;

    LimboLoginTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
    }

    @AfterEach
    void close() {
        world.close();
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static LimboLogin.Outcome said(LimboLogin login, String line) throws Exception {
        return login.say(READER, STEVE, NAME, line, "127.0.0.1").get(10, TimeUnit.SECONDS);
    }

    private static String told(LimboLogin.Outcome outcome) {
        assertThat(outcome).isInstanceOf(LimboLogin.Outcome.Stay.class);
        return plain(((LimboLogin.Outcome.Stay) outcome).said());
    }

    @Test
    @DisplayName("a new name is told to register, and two matching passwords make the account and let it in")
    void aNewNameRegisters() throws Exception {
        LimboLogin login = world.limboLogin(true);

        assertThat(plain(login.arrive(READER, STEVE, NAME, NONE).getLast())).contains("/register");
        assertThat(said(login, "/register hunter22 hunter22"))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
        assertThat(world.accounts.findByName(NAME)).isPresent();
    }

    @Test
    @DisplayName("a register that wants the password twice says so, and two that differ are told apart")
    void aRegisterIsChecked() throws Exception {
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/register hunter22"))).isNotBlank();
        assertThat(told(said(login, "/reg hunter22 hunter23"))).contains("not the same");
        assertThat(told(said(login, "/register abc abc"))).contains("6");
    }

    @Test
    @DisplayName("where the operator asks the password once, one is enough to register")
    void onePasswordIsEnoughWhenAskedOnce() throws Exception {
        LimboLogin login = world.limboLogin(false);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(said(login, "/register hunter22")).isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
    }

    @Test
    @DisplayName("a known name logs in with its password, in any of its words, and a wrong one says the tries left")
    void aKnownNameLogsIn() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);

        LimboLogin login = world.limboLogin(true);
        assertThat(plain(login.arrive(READER, STEVE, NAME, NONE).getLast())).contains("/login");
        assertThat(told(said(login, "/login wrongpass"))).contains("2");
        assertThat(said(login, "/giris hunter22")).isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("too many wrong passwords send the player away with the reason")
    void tooManyWrongPasswordsSendAway() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        said(login, "/login one111");
        said(login, "/login two222");

        assertThat(said(login, "/login three3")).isInstanceOf(LimboLogin.Outcome.Out.class);
    }

    @Test
    @DisplayName("anything else said in the waiting world is answered with what to type")
    void anythingElseIsAnsweredWithWhatToType() throws Exception {
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "hello"))).contains("/register");
        assertThat(told(said(login, "/spawn"))).contains("/register");
        assertThat(told(said(login, "/login"))).contains("/register");
    }

    @Test
    @DisplayName(
            "a player the door let in unverified makes an unverified account, and one whose handshake failed reads why")
    void theDoorsFlagsReachTheWait() throws Exception {
        LimboLogin login = world.limboLogin(true);

        java.util.List<Component> explained = login.arrive(READER, STEVE, NAME, new Claim.Waiting(false, true, false));
        assertThat(explained).hasSize(2);
        assertThat(plain(explained.getFirst())).contains("paid");

        java.util.List<Component> lines = login.arrive(READER, STEVE, NAME, new Claim.Waiting(true, false, false));

        assertThat(lines).hasSize(1);
        assertThat(said(login, "/register hunter22 hunter22"))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
        assertThat(world.accounts.findByName(NAME))
                .hasValueSatisfying(account -> assertThat(account.unverified()).isTrue());
    }

    @Test
    @DisplayName("a wrong password that bans the address sends the player away with the time left")
    void aBanSendsAway() throws Exception {
        java.util.List<String> hosts = new java.util.ArrayList<>();
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        LimboLogin login = world.limboLogin(true, (address, host) -> {
            hosts.add(address + "@" + host);
            return java.util.Optional.of(java.time.Duration.ofMinutes(5));
        });
        login.arrive(READER, STEVE, NAME, NONE, "mc.tcpshield.com");

        LimboLogin.Outcome outcome = said(login, "/login one111");

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        LimboLogin.Outcome.Out.class,
                        out -> assertThat(plain(out.reason())).contains("5m"));
        assertThat(hosts).containsExactly("127.0.0.1@mc.tcpshield.com");
    }
}
