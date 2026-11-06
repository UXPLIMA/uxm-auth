package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.application.premium.MemoryAccounts;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.UuidScheme;
import org.junit.jupiter.api.Test;

class ReadTheNameTest {

    private final MemoryAccounts accounts = new MemoryAccounts();
    private final ReadTheName names = new ReadTheName(
            accounts, () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()), () -> 3);

    @Test
    void aNewNameKeepsTheIdItWouldHaveWhileNobodyHoldsIt() {
        UUID offline = UuidScheme.offline(new PlayerName("Notch"));

        assertThat(names.newcomerId(offline)).isEqualTo(offline);
    }

    @Test
    void aNewNameNeverTakesTheIdOfAnAccountThatMovedAwayFromIt() {
        UUID offline = UuidScheme.offline(new PlayerName("Notch"));
        accounts.cracked(offline, "Notch_");

        UUID given = names.newcomerId(offline);

        assertThat(given).isNotEqualTo(offline);
        assertThat(accounts.findById(given)).isEmpty();
    }
}
