package com.uxplima.auth.domain.protection;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

/**
 * A captcha code drawn for the eye, on the square a map shows: thick letters, each at a height of its own, under
 * dots and lines a reader looks past and a program that reads the letters trips on. The font is our own, five
 * cells by seven, three pixels to a cell.
 */
public final class CaptchaPicture {

    /** The side of a map, in pixels. */
    public static final int SIZE = 128;

    /** The longest code a map shows at this size. */
    public static final int LONGEST = 7;

    private static final int COLUMNS = 5;
    private static final int ROWS = 7;
    private static final int SCALE = 3;
    private static final int ADVANCE = (COLUMNS + 1) * SCALE;
    private static final int HIGHEST_LIFT = 16;
    private static final int DOTS = SIZE * SIZE / 40;
    private static final int LINES = 3;

    private static final Map<Character, String> FONT = Map.ofEntries(
            Map.entry('0', ".###.#...##..###.#.###..##...#.###."),
            Map.entry('1', "..#...##....#....#....#....#...###."),
            Map.entry('2', ".###.#...#....#...#...#...#...#####"),
            Map.entry('3', "#####...#...#.....#.....##...#.###."),
            Map.entry('4', "...#...##..#.#.#..#.#####...#....#."),
            Map.entry('5', "######....####.....#....##...#.###."),
            Map.entry('6', "..##..#...#....####.#...##...#.###."),
            Map.entry('7', "#####....#...#...#...#....#....#..."),
            Map.entry('8', ".###.#...##...#.###.#...##...#.###."),
            Map.entry('9', ".###.#...##...#.####....#...#..##.."),
            Map.entry('A', ".###.#...##...#######...##...##...#"),
            Map.entry('B', "####.#...##...#####.#...##...#####."),
            Map.entry('C', ".###.#...##....#....#....#...#.###."),
            Map.entry('D', "###..#..#.#...##...##...##..#.###.."),
            Map.entry('E', "######....#....####.#....#....#####"),
            Map.entry('F', "######....#....####.#....#....#...."),
            Map.entry('G', ".###.#...##....#.####...##...#.####"),
            Map.entry('H', "#...##...##...#######...##...##...#"),
            Map.entry('I', ".###...#....#....#....#....#...###."),
            Map.entry('J', "..###...#....#....#....#.#..#..##.."),
            Map.entry('K', "#...##..#.#.#..##...#.#..#..#.#...#"),
            Map.entry('L', "#....#....#....#....#....#....#####"),
            Map.entry('M', "#...###.###.#.##.#.##...##...##...#"),
            Map.entry('N', "#...##...###..##.#.##..###...##...#"),
            Map.entry('O', ".###.#...##...##...##...##...#.###."),
            Map.entry('P', "####.#...##...#####.#....#....#...."),
            Map.entry('Q', ".###.#...##...##...##.#.##..#..##.#"),
            Map.entry('R', "####.#...##...#####.#.#..#..#.#...#"),
            Map.entry('S', ".#####....#.....###.....#....#####."),
            Map.entry('T', "#####..#....#....#....#....#....#.."),
            Map.entry('U', "#...##...##...##...##...##...#.###."),
            Map.entry('V', "#...##...##...##...##...#.#.#...#.."),
            Map.entry('W', "#...##...##...##.#.##.#.##.#.#.#.#."),
            Map.entry('X', "#...##...#.#.#...#...#.#.#...##...#"),
            Map.entry('Y', "#...##...#.#.#...#....#....#....#.."),
            Map.entry('Z', "#####....#...#...#...#...#....#####"));

    private final boolean[] ink;

    private CaptchaPicture(boolean[] ink) {
        this.ink = ink;
    }

    /** Whether every character of this text is one the font draws. */
    public static boolean drawable(String text) {
        return Objects.requireNonNull(text, "text").chars().allMatch(c -> FONT.containsKey((char) c));
    }

    /** The code, each letter at a height of its own, under noise, all of it from this generator. */
    public static CaptchaPicture draw(String code, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        CaptchaPicture picture = letters(code, lifts(checked(code).length(), random));
        picture.noise(random);
        return picture;
    }

    /** A height for each letter, up or down by as much as the map leaves room for. */
    static int[] lifts(int letters, RandomGenerator random) {
        int[] lifts = new int[letters];
        for (int i = 0; i < letters; i++) {
            lifts[i] = random.nextInt(-HIGHEST_LIFT, HIGHEST_LIFT + 1);
        }
        return lifts;
    }

    /** The code alone, letter i lifted by {@code lifts[i]} pixels, a negative lift moving it up. */
    static CaptchaPicture letters(String code, int[] lifts) {
        checked(code);
        CaptchaPicture picture = new CaptchaPicture(new boolean[SIZE * SIZE]);
        int left = (SIZE - code.length() * ADVANCE) / 2;
        int top = (SIZE - ROWS * SCALE) / 2;
        for (int i = 0; i < code.length(); i++) {
            picture.letter(FONT.get(code.charAt(i)), left + i * ADVANCE, top + lifts[i]);
        }
        return picture;
    }

    /** Whether the pixel at this column and row is dark. */
    public boolean ink(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && ink[y * SIZE + x];
    }

    private static String checked(String code) {
        Objects.requireNonNull(code, "code");
        if (code.isEmpty() || code.length() > LONGEST) {
            throw new IllegalArgumentException(
                    "A captcha code on a map is 1 to " + LONGEST + " characters, got " + code.length());
        }
        if (!drawable(code)) {
            throw new IllegalArgumentException("A captcha code is drawn from A to Z and 0 to 9 only");
        }
        return code;
    }

    private void letter(@Nullable String glyph, int left, int top) {
        String cells = Objects.requireNonNull(glyph, "glyph");
        for (int cell = 0; cell < cells.length(); cell++) {
            if (cells.charAt(cell) == '#') {
                fill(left + cell % COLUMNS * SCALE, top + cell / COLUMNS * SCALE);
            }
        }
    }

    private void fill(int left, int top) {
        for (int y = top; y < top + SCALE; y++) {
            for (int x = left; x < left + SCALE; x++) {
                set(x, y);
            }
        }
    }

    private void noise(RandomGenerator random) {
        for (int i = 0; i < DOTS; i++) {
            set(random.nextInt(SIZE), random.nextInt(SIZE));
        }
        for (int i = 0; i < LINES; i++) {
            int from = random.nextInt(SIZE);
            int to = random.nextInt(SIZE);
            for (int x = 0; x < SIZE; x++) {
                set(x, from + (to - from) * x / (SIZE - 1));
            }
        }
    }

    private void set(int x, int y) {
        if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
            ink[y * SIZE + x] = true;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CaptchaPicture picture && Arrays.equals(ink, picture.ink);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ink);
    }

    @Override
    public String toString() {
        return "CaptchaPicture[" + SIZE + "x" + SIZE + "]";
    }
}
