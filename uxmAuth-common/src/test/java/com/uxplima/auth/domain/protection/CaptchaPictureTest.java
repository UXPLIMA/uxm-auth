package com.uxplima.auth.domain.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.SplittableRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The code drawn on a map: thick letters a reader sees, under noise a parser trips on. */
final class CaptchaPictureTest {

    private static int ink(CaptchaPicture picture) {
        int count = 0;
        for (int y = 0; y < CaptchaPicture.SIZE; y++) {
            for (int x = 0; x < CaptchaPicture.SIZE; x++) {
                count += picture.ink(x, y) ? 1 : 0;
            }
        }
        return count;
    }

    @Test
    @DisplayName("a letter is drawn three pixels to one, centred, at the height it was lifted to")
    void aLetterIsDrawnLarge() {
        CaptchaPicture picture = CaptchaPicture.letters("L", new int[] {0});

        // One letter of 5 by 7 cells, 3 pixels each, in the middle of 128.
        int left = (CaptchaPicture.SIZE - 18) / 2;
        int top = (CaptchaPicture.SIZE - 21) / 2;
        for (int y = top; y < top + 21; y++) {
            assertThat(picture.ink(left, y)).as("the stem at row %d", y).isTrue();
            assertThat(picture.ink(left + 3, y)).as("the foot at row %d", y).isEqualTo(y >= top + 18);
        }
        assertThat(picture.ink(left + 14, top + 20)).isTrue();
        assertThat(picture.ink(left + 14, top + 17)).isFalse();
        assertThat(picture.ink(left - 1, top)).isFalse();
        assertThat(ink(picture)).isEqualTo((7 + 4) * 9);
    }

    @Test
    @DisplayName("a lifted letter moves up by its lift, and every letter of the code takes its own place")
    void lettersTakeTheirPlaces() {
        CaptchaPicture flat = CaptchaPicture.letters("HH", new int[] {0, 0});
        CaptchaPicture lifted = CaptchaPicture.letters("HH", new int[] {-5, 0});

        int left = (CaptchaPicture.SIZE - 36) / 2;
        int top = (CaptchaPicture.SIZE - 21) / 2;
        assertThat(flat.ink(left, top)).isTrue();
        assertThat(flat.ink(left + 18, top)).isTrue();
        assertThat(lifted.ink(left, top - 5)).isTrue();
        assertThat(lifted.ink(left, top + 20)).isFalse();
        assertThat(lifted.ink(left + 18, top + 20)).isTrue();
    }

    @Test
    @DisplayName("the noise differs from one showing to the next, and the letters stay under it")
    void theNoiseChangesAndTheLettersStay() {
        CaptchaPicture one = CaptchaPicture.draw("AB7", new SplittableRandom(1));
        CaptchaPicture two = CaptchaPicture.draw("AB7", new SplittableRandom(2));
        CaptchaPicture bare = CaptchaPicture.letters("AB7", new int[] {0, 0, 0});

        assertThat(one).isNotEqualTo(two);
        assertThat(ink(one)).isGreaterThan(ink(bare) / 2).isLessThan(CaptchaPicture.SIZE * CaptchaPicture.SIZE / 4);
        assertThat(CaptchaPicture.draw("AB7", new SplittableRandom(1))).isEqualTo(one);
    }

    @Test
    @DisplayName("each letter is lifted up or down by as much as sixteen pixels")
    void lettersAreLiftedBothWays() {
        int[] lifts = CaptchaPicture.lifts(400, new SplittableRandom(5));

        assertThat(java.util.Arrays.stream(lifts).min().orElseThrow()).isEqualTo(-16);
        assertThat(java.util.Arrays.stream(lifts).max().orElseThrow()).isEqualTo(16);
    }

    @Test
    @DisplayName("the noise is hundreds of dots and lines that slope across the whole width")
    void theNoiseIsDotsAndSlopingLines() {
        CaptchaPicture noisy = CaptchaPicture.draw("AB7", new SplittableRandom(1));
        CaptchaPicture bare = CaptchaPicture.letters("AB7", CaptchaPicture.lifts(3, new SplittableRandom(1)));

        assertThat(ink(noisy) - ink(bare)).isGreaterThan(600);
        for (int y = 0; y < CaptchaPicture.SIZE; y++) {
            int row = 0;
            for (int x = 0; x < CaptchaPicture.SIZE; x++) {
                row += noisy.ink(x, y) ? 1 : 0;
            }
            assertThat(row).as("row %d", y).isLessThan(CaptchaPicture.SIZE / 2);
        }
    }

    @Test
    @DisplayName("nothing outside the square is ink, though the lines run from edge to edge")
    void nothingOutsideIsInk() {
        CaptchaPicture picture = CaptchaPicture.draw("AB7", new SplittableRandom(1));

        int edge = 0;
        for (int y = 0; y < CaptchaPicture.SIZE; y++) {
            edge += picture.ink(0, y) ? 1 : 0;
            assertThat(picture.ink(CaptchaPicture.SIZE, y)).isFalse();
            assertThat(picture.ink(-1, y)).isFalse();
            assertThat(picture.ink(y, CaptchaPicture.SIZE)).isFalse();
            assertThat(picture.ink(y, -1)).isFalse();
        }
        assertThat(edge).isPositive();
    }

    @Test
    @DisplayName("every letter and digit can be drawn, a longer code than the map holds or another sign cannot")
    void onlyWhatFitsIsDrawn() {
        assertThat(CaptchaPicture.drawable("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))
                .isTrue();
        assertThat(CaptchaPicture.drawable("a")).isFalse();
        assertThat(CaptchaPicture.drawable("Ç")).isFalse();
        assertThatThrownBy(() -> CaptchaPicture.draw("ABCDEFGH", new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7");
        assertThatThrownBy(() -> CaptchaPicture.draw("A-B", new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("no two letters are drawn alike")
    void noTwoLettersAlike() {
        String all = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < all.length(); i++) {
            for (int j = i + 1; j < all.length(); j++) {
                assertThat(CaptchaPicture.letters(all.substring(i, i + 1), new int[] {0}))
                        .as("%s and %s", all.charAt(i), all.charAt(j))
                        .isNotEqualTo(CaptchaPicture.letters(all.substring(j, j + 1), new int[] {0}));
            }
        }
    }
}
