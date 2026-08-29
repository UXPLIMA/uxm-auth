package com.uxplima.template.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.GlyphWidthTable;

/**
 * Centres the title of a chest window.
 *
 * <p>The canon writes a menu title centred and bare: no colour, no bold and no dashes around it, so the window
 * reads as part of the client's own frame instead of competing with the icons under it. The client draws the
 * title from a fixed origin and gives no way to align it, so the only way to centre it is to put spaces in
 * front of it.
 *
 * <p>The arithmetic is the client's own layout: the window is 176 pixels wide, the label starts 8 pixels in
 * from the left edge, and a space advances 4 pixels. Forgetting the origin is what makes a whole menu look
 * subtly wrong, because it pushes every title two spaces to the right and the rounding then lands differently
 * for each length.
 *
 * <p>The styling is dropped here rather than trusted to every catalog line: a title is flattened to plain text
 * and handed back as one plain component, so a key that still carries a colour cannot paint a two tone title.
 */
public final class MenuTitles {

    private static final int WINDOW_WIDTH = 176;

    private static final int TITLE_ORIGIN = 8;

    private static final String SPACE = " ";

    private MenuTitles() {}

    /** {@code title} with every colour removed and enough leading spaces to sit in the middle of the window. */
    public static Component centre(Component title) {
        Objects.requireNonNull(title, "title");
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (plain.isBlank()) {
            return title;
        }
        int free = WINDOW_WIDTH - 2 * TITLE_ORIGIN - widthOf(plain);
        int spaces = Math.round(free / (2f * GlyphWidthTable.SPACE_WIDTH));
        return Component.text(spaces <= 0 ? plain : SPACE.repeat(spaces) + plain);
    }

    private static int widthOf(String plain) {
        int total = 0;
        for (int index = 0; index < plain.length(); index++) {
            total += GlyphWidthTable.widthOf(plain.charAt(index), false);
        }
        return total;
    }
}
