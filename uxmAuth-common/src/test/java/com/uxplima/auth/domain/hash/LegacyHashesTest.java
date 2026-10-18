package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.hash.LegacyHashes.Known;
import com.uxplima.auth.domain.hash.LegacyHashes.Style;
import com.uxplima.auth.domain.hash.LegacyHashes.Unknown;
import com.uxplima.auth.domain.password.Password;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every hash another login plugin wrote is read, through the one form the plugin rewrites it into at the import.
 * The vectors were computed with Python's hashlib and bcrypt, for the password "hunter22".
 */
final class LegacyHashesTest {

    private static final String SALT = "7f3a9c2e1b5d4f60";
    private static final String DOUBLE_256 = "f7a3536b0757d0ce7ae6025a91557794e8fec6edee523bf8d7799532767fab4a";
    private static final String DOUBLE_512 = "0e0389e51138d62f6787ff6d307d0b9b730fb85a73e1c74ff93a811dacabc52d"
            + "0519d5061ec62659709908b7e0c7a24698ff990f8401869d45b29600188a2349";
    private static final String SALTED_256 = "488dda6192d1dd933011b2962575d940c9595bd8b6c607bf6145e0ed4be30dc3";
    private static final String SALTED_512 = "b6a58d1b130b9036dcf784f8825fd4eb5b6420ef39d2ca3e42bb6c07e68c82ce"
            + "7e78e16bb500d6caa7aa9d71d69c4f28ca0efd8b17e97bcf65c4fa8946bf0395";
    private static final String PLAIN_256 = "20d2fe5e369db54ec7090639a9dc30ec4d608604936239d39e2de07fda09eb0b";
    private static final String PLAIN_512 = "e633ac1a9852f4a28539d8e6525c79d426cc78b96f48176a46622125504c5e87"
            + "a9f27ac3d91620483883f6202856d8ac932ce764cce5952a526d399e43f32cf6";
    private static final String MD5 = "cb95015a436fe976eb38e45455372032";
    private static final String DOUBLE_PLAIN_256 = "a7ee532da68357152badceb5e386453217e6fba7f9efbb504c20dc0e6ca5daac";
    private static final String DJANGO = "pbkdf2_sha256$10000$Zx81kQp0aB$K7SHSVADi9bJlzX/eRrE4DLCy29TJM2JNsBtTEOcf9E=";
    private static final String BCRYPT_2Y = "$2y$04$4trO4mNFsdkV9E7I/VM9h..GCeN4iZXocw/5csRMigMCEwRXeqROG";

    /** The plugin's own hashing, with every reader it carries, writing Argon2id. */
    private static final Hashing HASHING = new Hashing(
            new Argon2idHasher(1024, 1, 1),
            List.of(
                    new BcryptHasher(4),
                    new AuthMeSha256Hasher(),
                    new Pbkdf2HexHasher(10_000),
                    new Pbkdf2DjangoHasher(),
                    new LegacyDigestHasher()));

    private static PasswordHash read(Style style, String raw, @Nullable String salt, @Nullable String algorithm) {
        LegacyHashes.Read read = LegacyHashes.rewrite(style, raw, salt, algorithm);
        assertThat(read)
                .as("%s reads %s", style, raw.substring(0, Math.min(8, raw.length())))
                .isInstanceOf(Known.class);
        return ((Known) read).hash();
    }

    private static void opens(PasswordHash hash) {
        assertThat(HASHING.verify(Password.of("hunter22"), hash))
                .as("the right password")
                .isInstanceOf(Verification.RightButRehash.class);
        assertThat(HASHING.verify(Password.of("hunter23"), hash))
                .as("a wrong one")
                .isInstanceOf(Verification.Wrong.class);
    }

    @Test
    @DisplayName("the double salted digests read in every layout that writes them")
    void doubleSaltedDigests() {
        opens(read(Style.DOLLAR_SHA, "$SHA$" + SALT + "$" + DOUBLE_256, null, null));
        opens(read(Style.DOLLAR_SHA, "$SHA$" + SALT + "$" + DOUBLE_512, null, null));
        opens(read(Style.HASH_FIRST, "$SHA512$" + DOUBLE_512 + "$" + SALT, null, null));
        opens(read(Style.HASH_FIRST, "$SHA$" + DOUBLE_512 + "$" + SALT, null, null));
        opens(read(Style.NAME_SALT_HASH, "SHA256$" + SALT + "$" + DOUBLE_256, null, null));
        opens(read(Style.NAME_SALT_HASH, "SHA512$" + SALT + "$" + DOUBLE_512, null, null));
        opens(read(Style.SALT_COLUMN, DOUBLE_512, SALT, null));
        opens(read(Style.ALGORITHM_COLUMN, DOUBLE_256, SALT, "SHA-256"));
        opens(read(Style.ALGORITHM_COLUMN, DOUBLE_512, SALT, "SHA-512"));
    }

    @Test
    @DisplayName("the single salted, the unsalted and the twice hashed digests read")
    void otherDigests() {
        opens(read(Style.SALTED, "$SHA$" + SALT + "$" + SALTED_256, null, null));
        opens(read(Style.SALTED, "SHA$" + SALT + "$" + SALTED_512, null, null));
        opens(read(Style.PLAIN, "$SHA$" + PLAIN_256, null, null));
        opens(read(Style.PLAIN, PLAIN_256, null, null));
        opens(read(Style.PLAIN, PLAIN_512, null, null));
        opens(read(Style.PLAIN, MD5, null, null));
        opens(read(Style.ALGORITHM_COLUMN, PLAIN_512, null, "SHA-512"));
        opens(read(Style.TWICE, DOUBLE_PLAIN_256, null, null));
    }

    @Test
    @DisplayName("bcrypt under any of its prefixes, Django's PBKDF2 and every format the plugin reads pass as they are")
    void formatsReadAsTheyAre() {
        opens(read(Style.AS_IS, BCRYPT_2Y, null, null));
        opens(read(Style.NAME_SALT_HASH, "BCRYPT$" + BCRYPT_2Y.substring("$2y$".length()), null, null));
        opens(read(Style.AS_IS, DJANGO, null, null));
        assertThat(HashFormats.recognise(new PasswordHash(DJANGO))).contains(HashAlgorithm.PBKDF2_DJANGO);
        assertThat(read(Style.DOLLAR_SHA, BCRYPT_2Y, null, null).encoded())
                .as("a source that moved some hashes on keeps them")
                .isEqualTo(BCRYPT_2Y);
    }

    @Test
    @DisplayName("a format nobody reads is named by its prefix alone, and nothing it holds is kept")
    void anUnknownFormatIsNamed() {
        assertThat(LegacyHashes.rewrite(Style.AS_IS, "$md5crypt$abc$def0123456789", null, null))
                .isInstanceOfSatisfying(
                        Unknown.class, unknown -> assertThat(unknown.format()).isEqualTo("$md5crypt$"));
        assertThat(LegacyHashes.rewrite(Style.ALGORITHM_COLUMN, DOUBLE_256, SALT, "WHIRLPOOL"))
                .isInstanceOfSatisfying(
                        Unknown.class, unknown -> assertThat(unknown.format()).isEqualTo("WHIRLPOOL"));
        assertThat(LegacyHashes.rewrite(Style.SALT_COLUMN, DOUBLE_512, "has$dollar", null))
                .isInstanceOf(Unknown.class);
        assertThat(LegacyHashes.rewrite(Style.PLAIN, "abc", null, null)).isInstanceOf(Unknown.class);
        assertThat(LegacyHashes.rewrite(Style.AS_IS, "$SHA$" + DOUBLE_512 + "$" + SALT, null, null))
                .as("a $SHA$ hash in another shape is not the website's, and only its layout can say what it is")
                .isInstanceOfSatisfying(
                        Unknown.class, unknown -> assertThat(unknown.format()).isEqualTo("$SHA$"));
        assertThat(LegacyHashes.rewrite(Style.AS_IS, "", null, null)).isInstanceOf(Unknown.class);
    }

    @Test
    @DisplayName("a rewritten digest is a reader only: it is never current, so the next login moves it on")
    void aDigestIsNeverCurrent() {
        PasswordHash digest = read(Style.PLAIN, PLAIN_256, null, null);

        assertThat(digest.encoded()).startsWith("$digest$sha256$plain$");
        assertThat(new LegacyDigestHasher().isCurrent(digest)).isFalse();
        assertThat(HashFormats.recognise(digest)).contains(HashAlgorithm.LEGACY_DIGEST);
        assertThat(Style.of("name-salt-hash")).isEqualTo(Style.NAME_SALT_HASH);
    }
}
