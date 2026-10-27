package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.Test;

class HashingTest {

    // Made outside this code base: AuthMe's double SHA-256 and the bcrypt reference library, both of "hunter22".
    private static final PasswordHash AUTHME =
            new PasswordHash("$SHA$a1b2c3d4e5f6a7b8$db136337234cc82d6d0363b07ec32e8cd4a8fc7900ba6b1b227f1d6e6afc50d4");
    private static final PasswordHash BCRYPT_2A =
            new PasswordHash("$2a$04$Vvbi/R2HR./OzPefdB.66OAu8wK2I1GwCYpb7MQqaWC67lcIVSeGG");
    private static final PasswordHash BCRYPT_2B =
            new PasswordHash("$2b$04$YLhhJuzGDKeYnVSpmY8kbu5jm4jDc2e6rFdUifCB/k5pQA9MlJ0By");

    private final Argon2idHasher argon = new Argon2idHasher(64, 1, 1);
    private final BcryptHasher bcrypt = new BcryptHasher(4);
    private final Hashing hashing = new Hashing(argon, List.of(bcrypt, new AuthMeSha256Hasher()));

    private static Password pw(String typed) {
        return Password.of(typed);
    }

    @Test
    void aFreshHashVerifiesAndNeedsNothingElse() {
        PasswordHash stored = hashing.hash(pw("hunter22"));

        assertThat(HashFormats.recognise(stored)).contains(HashAlgorithm.ARGON2ID);
        assertThat(hashing.verify(pw("hunter22"), stored)).isInstanceOf(Verification.Right.class);
        assertThat(hashing.verify(pw("hunter23"), stored)).isInstanceOf(Verification.Wrong.class);
    }

    @Test
    void twoHashesOfOnePasswordDiffer() {
        assertThat(hashing.hash(pw("hunter22"))).isNotEqualTo(hashing.hash(pw("hunter22")));
    }

    @Test
    void anImportedAuthMeHashLogsInAndIsRehashedToTheCurrentAlgorithm() {
        Verification result = hashing.verify(pw("hunter22"), AUTHME);

        assertThat(result).isInstanceOfSatisfying(Verification.RightButRehash.class, rehash -> {
            assertThat(HashFormats.recognise(rehash.fresh())).contains(HashAlgorithm.ARGON2ID);
            assertThat(hashing.verify(pw("hunter22"), rehash.fresh())).isInstanceOf(Verification.Right.class);
        });
        assertThat(hashing.verify(pw("Hunter22"), AUTHME)).isInstanceOf(Verification.Wrong.class);
    }

    @Test
    void bcryptHashesFromOtherSoftwareVerify() {
        assertThat(hashing.verify(pw("hunter22"), BCRYPT_2A)).isInstanceOf(Verification.RightButRehash.class);
        assertThat(hashing.verify(pw("hunter22"), BCRYPT_2B)).isInstanceOf(Verification.RightButRehash.class);
        assertThat(hashing.verify(pw("hunter2"), BCRYPT_2B)).isInstanceOf(Verification.Wrong.class);
    }

    @Test
    void aHashWithTheCurrentAlgorithmButOlderSettingsIsRehashed() {
        Hashing stronger = new Hashing(new BcryptHasher(5), List.of());

        assertThat(stronger.verify(pw("hunter22"), BCRYPT_2B)).isInstanceOf(Verification.RightButRehash.class);
        assertThat(new Hashing(bcrypt, List.of()).verify(pw("hunter22"), BCRYPT_2B))
                .isInstanceOf(Verification.Right.class);
    }

    @Test
    void aHashNoReaderKnowsNeverLogsIn() {
        Hashing argonOnly = new Hashing(argon, List.of());

        assertThat(argonOnly.verify(pw("hunter22"), AUTHME)).isInstanceOf(Verification.Wrong.class);
        assertThat(hashing.verify(pw("hunter22"), new PasswordHash("plain-text")))
                .isInstanceOf(Verification.Wrong.class);
    }

    @Test
    void aHashNeverPrintsItself() {
        assertThat(AUTHME.toString()).doesNotContain("db1363");
    }
}
