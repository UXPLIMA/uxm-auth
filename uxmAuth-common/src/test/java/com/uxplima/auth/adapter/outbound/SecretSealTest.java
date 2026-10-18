package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.SplittableRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A second-factor secret is kept sealed, so a copy of the database hands nobody a staff member's codes. */
final class SecretSealTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path folder;

    @Test
    @DisplayName("a sealed secret opens back under the same key, and two seals of one secret differ")
    void sealAndOpen() {
        SecretSeal seal = SecretSeal.fromPassphrase("network secret");

        String once = seal.seal(SECRET);
        String twice = seal.seal(SECRET);

        assertThat(once).isNotEqualTo(twice).doesNotContain("1234567890");
        assertThat(seal.open(once)).isEqualTo(SECRET);
        assertThat(SecretSeal.fromPassphrase("network secret").open(twice)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("another key, or a sealed text changed by one character, opens nothing")
    void anotherKeyOpensNothing() {
        String sealed = SecretSeal.fromPassphrase("network secret").seal(SECRET);
        char last = sealed.charAt(sealed.length() - 2);
        String changed = sealed.substring(0, sealed.length() - 2)
                + (last == 'A' ? 'B' : 'A')
                + sealed.charAt(sealed.length() - 1);

        assertThatThrownBy(() -> SecretSeal.fromPassphrase("another").open(sealed))
                .isInstanceOf(SecretSeal.Unreadable.class);
        assertThatThrownBy(() -> SecretSeal.fromPassphrase("network secret").open(changed))
                .isInstanceOf(SecretSeal.Unreadable.class);
        assertThatThrownBy(() -> SecretSeal.fromPassphrase("network secret").open("not sealed"))
                .isInstanceOf(SecretSeal.Unreadable.class);
    }

    @Test
    @DisplayName("on a server alone the key is made once in the data folder and read back at every start")
    void theKeyFileIsMadeOnce() throws Exception {
        Path file = folder.resolve("factor.key");

        String sealed = SecretSeal.fromKeyFile(file, new SplittableRandom(3)).seal(SECRET);
        String written = Files.readString(file);

        assertThat(SecretSeal.fromKeyFile(file, new SplittableRandom(4)).open(sealed))
                .isEqualTo(SECRET);
        assertThat(Files.readString(file)).isEqualTo(written);
        assertThat(java.util.Base64.getDecoder().decode(written.strip())).hasSize(32);
        assertThat(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                .isEqualTo("rw-------");
    }

    @Test
    @DisplayName("a key file that holds a key of another length is refused, never used as it is")
    void aKeyOfAnotherLengthIsRefused() throws Exception {
        Path file = folder.resolve("short.key");
        Files.writeString(file, Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> SecretSeal.fromKeyFile(file, new SplittableRandom(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("an empty passphrase is no key")
    void anEmptyPassphraseIsRefused() {
        assertThatThrownBy(() -> SecretSeal.fromPassphrase(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
