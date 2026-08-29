package com.uxplima.template.adapter.outbound.style;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Puts the title of a menu tile where the canon keeps it: on the first line of the lore, under a blank name.
 *
 * <p>The client draws the display name hard against the top edge of the tooltip and will not put a line above
 * it. A blank name buys that line of air, and the title then reads as the first thing inside the tooltip
 * rather than as its lid. A blank line closes the tooltip the same way, so the text sits in a box of air.
 *
 * <p>The blank name is a single space and not an empty component. An empty name makes the client fall back to
 * the name of the material, and the tile then says "Ender Eye" where the blank line belongs.
 *
 * <p>A button is not a tile. A page arrow or a filler pane keeps its one line name and carries no lore at all,
 * so {@link #titled} hands such an item back untouched.
 */
public final class Tiles {

    private static final String DIAMOND = "◆ ";

    /** Every lore line is padded one space either side, so no text touches the edge of the tooltip. */
    static final String PADDING = " ";

    private Tiles() {}

    /** The name a titled tile carries. */
    public static Component blankName() {
        return Component.text(PADDING);
    }

    /**
     * The lore of a tile: the title line, the lore as it was written, then the closing blank. The lore is one
     * component with newlines in it, which the uxmLib item builder splits into lines.
     */
    public static Component titled(Theme theme, Component title, Component lore) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        if (isBlank(title)) {
            return lore;
        }
        return head(theme, title)
                .append(Component.newline())
                .append(lore)
                .append(Component.newline())
                .append(Component.text(PADDING));
    }

    /** The title line on its own: the diamond in the icon grey, then the title in bold. */
    public static Component head(Theme theme, Component title) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        return Component.text(PADDING)
                .append(Component.text(DIAMOND, theme.colour("icon")))
                .append(title.decoration(TextDecoration.BOLD, true))
                .append(Component.text(PADDING));
    }

    /** Whether {@code title} would put a title on a tile, or is the blank that a titled tile already carries. */
    public static boolean isBlank(Component title) {
        Objects.requireNonNull(title, "title");
        return PlainTextComponentSerializer.plainText().serialize(title).isBlank();
    }
}
