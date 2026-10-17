package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A name is read at the door: the operator's rules first, then the case it was registered in. */
final class ReadTheNameTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final NamePolicy policy = new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of("admin"));
    private final ReadTheName useCase = new ReadTheName(fixture.accounts, () -> policy, () -> AuthFixture.MAX_ATTEMPTS);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("a name the operator's rules refuse is refused with what the rules said")
    void aNameTheRulesRefuseIsRefused() {
        assertThat(useCase.read("ab")).isEqualTo(new ReadTheName.Reading.Refused(new NameVerdict.TooShort(3)));
        assertThat(useCase.read("Steve!")).isEqualTo(new ReadTheName.Reading.Refused(new NameVerdict.BadCharacters()));
        assertThat(useCase.read("xAdMinx"))
                .isEqualTo(new ReadTheName.Reading.Refused(new NameVerdict.Blocked("admin")));
    }

    @Test
    @DisplayName("a registered name in another case is refused, naming the case it was registered in")
    void anotherCaseIsRefusedNamingTheRegisteredOne() {
        fixture.accounts.seed(AuthFixture.account("Notch", fixture.hashing.hash(Password.of("hunter22"))));

        assertThat(useCase.read("notch"))
                .isEqualTo(new ReadTheName.Reading.Refused(new NameVerdict.WrongCase(new PlayerName("Notch"))));
    }

    @Test
    @DisplayName("a registered name in its own case waits for its password")
    void theRegisteredCaseWaitsForItsPassword() {
        Account notch = AuthFixture.account("Notch", fixture.hashing.hash(Password.of("hunter22")));
        fixture.accounts.seed(notch);

        assertThat(useCase.read("Notch"))
                .isInstanceOfSatisfying(
                        ReadTheName.Reading.Opens.class,
                        opens -> assertThat(opens.state())
                                .isInstanceOfSatisfying(AuthState.Registered.class, registered -> {
                                    assertThat(registered.account().id()).isEqualTo(notch.id());
                                    assertThat(registered.attempts().left()).isEqualTo(AuthFixture.MAX_ATTEMPTS);
                                }));
    }

    @Test
    @DisplayName("a name with no account is asked to register")
    void anUnknownNameRegisters() {
        assertThat(useCase.read("Newcomer"))
                .isEqualTo(new ReadTheName.Reading.Opens(new AuthState.Unregistered(new PlayerName("Newcomer"))));
    }

    @Test
    @DisplayName("a Bedrock name is held only to the blocked words, since its form is Xbox's and Floodgate's")
    void aBedrockNameIsHeldOnlyToTheBlockedWords() {
        assertThat(useCase.readBedrock(".St")).isEmpty();
        assertThat(useCase.readBedrock(".Steve Two")).isEmpty();
        assertThat(useCase.readBedrock(".TheAdmin")).contains(new NameVerdict.Blocked("admin"));
    }
}
