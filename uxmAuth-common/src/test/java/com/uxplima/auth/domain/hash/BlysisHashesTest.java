package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.Test;

class BlysisHashesTest {

    // Made outside this code base, by Blysis' own code: scryptHash of password-hash.ts on Node's crypto.scrypt, and
    // bcryptjs 3 at Blysis' 12 rounds. All three are of "hunter22" but the second scrypt, which is of "şifre-Ünicode1".
    private static final PasswordHash SCRYPT = new PasswordHash(
            "$scrypt$N=65536,r=8,p=1$7r+XYV8QcDINRp1XLH0C8A==$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E=");
    private static final PasswordHash SCRYPT_UNICODE = new PasswordHash(
            "$scrypt$N=65536,r=8,p=1$Vh6l1b5Ygd86Ki5wB2VaUQ==$amHJlt1zt6fSrq+X4uuSTOxq7pG9dDxVUmkBeNPUCdY=");
    private static final PasswordHash BCRYPTJS =
            new PasswordHash("$2b$12$r.v55D51cxJWSxtdFbz1/.5ifAK486O6b4kDkiyNaXmqEUnbsLF8u");
    // AuthMe's double SHA-256, which a website imports from an older one.
    private static final PasswordHash AUTHME =
            new PasswordHash("$SHA$a1b2c3d4e5f6a7b8$db136337234cc82d6d0363b07ec32e8cd4a8fc7900ba6b1b227f1d6e6afc50d4");

    private final ScryptHasher scrypt = new ScryptHasher();
    /** What a server whose passwords Blysis keeps reads with: bcrypt at Blysis' cost first, and every other. */
    private final Hashing blysis = new Hashing(
            new BcryptHasher(12), List.of(new ScryptHasher(), new AuthMeSha256Hasher(), new Argon2idHasher(64, 1, 1)));

    private static Password pw(String typed) {
        return Password.of(typed);
    }

    @Test
    void blysisScryptIsRecognisedAndVerifies() {
        assertThat(HashFormats.recognise(SCRYPT)).contains(HashAlgorithm.SCRYPT);
        assertThat(scrypt.verify(pw("hunter22"), SCRYPT)).isTrue();
        assertThat(scrypt.verify(pw("hunter23"), SCRYPT)).isFalse();
        assertThat(scrypt.verify(pw("şifre-Ünicode1"), SCRYPT_UNICODE)).isTrue();
        assertThat(scrypt.verify(pw("sifre-Unicode1"), SCRYPT_UNICODE)).isFalse();
    }

    @Test
    void aScryptMemberIsMovedToBcryptAtTheirLogin() {
        assertThat(blysis.verify(pw("hunter22"), SCRYPT))
                .isInstanceOfSatisfying(Verification.RightButRehash.class, rehash -> {
                    assertThat(rehash.fresh().encoded()).startsWith("$2b$12$");
                    assertThat(blysis.verify(pw("hunter22"), rehash.fresh())).isInstanceOf(Verification.Right.class);
                });
        assertThat(blysis.verify(pw("hunter2"), SCRYPT)).isInstanceOf(Verification.Wrong.class);
    }

    @Test
    void aBcryptMemberIsLeftAsBlysisWroteIt() {
        assertThat(blysis.verify(pw("hunter22"), BCRYPTJS)).isInstanceOf(Verification.Right.class);
        assertThat(blysis.verify(pw("Hunter22"), BCRYPTJS)).isInstanceOf(Verification.Wrong.class);
    }

    @Test
    void anAuthMeHashAWebsiteImportedIsMovedToBcrypt() {
        assertThat(blysis.verify(pw("hunter22"), AUTHME))
                .isInstanceOfSatisfying(
                        Verification.RightButRehash.class,
                        rehash -> assertThat(HashFormats.recognise(rehash.fresh()))
                                .contains(HashAlgorithm.BCRYPT));
    }

    @Test
    void aFreshScryptHashIsInBlysisFormAndCurrent() {
        PasswordHash fresh = scrypt.hash(pw("hunter22"));

        assertThat(fresh.encoded()).matches("^\\$scrypt\\$N=65536,r=8,p=1\\$[A-Za-z0-9+/]{22}==\\$[A-Za-z0-9+/]{43}=$");
        assertThat(scrypt.verify(pw("hunter22"), fresh)).isTrue();
        assertThat(scrypt.isCurrent(fresh)).isTrue();
        assertThat(
                        scrypt.isCurrent(
                                new PasswordHash(
                                        "$scrypt$N=16384,r=8,p=1$7r+XYV8QcDINRp1XLH0C8A==$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E=")))
                .isFalse();
    }

    @Test
    void aHashNodeWouldNotRunIsNoMatch() {
        // N past the memory Node gives Blysis, N not a power of two, a cost of one, and Base64 that is not.
        for (String claimed : new String[] {
            "$scrypt$N=1048576,r=8,p=1$7r+XYV8QcDINRp1XLH0C8A==$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E=",
            "$scrypt$N=65535,r=8,p=1$7r+XYV8QcDINRp1XLH0C8A==$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E=",
            "$scrypt$N=1,r=8,p=1$7r+XYV8QcDINRp1XLH0C8A==$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E=",
            "$scrypt$N=65536,r=8,p=1$====$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E=",
            "$scrypt$N=65536,r=8$7r+XYV8QcDINRp1XLH0C8A==$oqqw890xSby0di9OWzn9/ux4GZ97OU9o8aXux/tB16E="
        }) {
            assertThat(scrypt.verify(pw("hunter22"), new PasswordHash(claimed)))
                    .as(claimed)
                    .isFalse();
        }
    }
}
