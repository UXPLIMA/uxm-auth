package com.uxplima.template.adapter.outbound.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.Text;

/**
 * Builds the lore of a menu tile in the one order the canon allows:
 *
 * <pre>
 *  ◆ title            (from Tiles)
 *    breadcrumb
 *  (blank)
 *  ✎ description      the header, then the sentences
 *  (blank)
 *  ≡ details          the header, then the facts
 *  ▪ label value
 *  (blank)
 *  → click to do the thing
 * </pre>
 *
 * <p>The structure is here and the words are in the message catalog, which is the split that matters: a
 * translator moves every word and can move none of the shape, so no tile can be shipped without its
 * breadcrumb, its description header or its click line. The glyphs are structure rather than words, so they
 * do not belong in the catalog either.
 *
 * <p>Blocks are separated by a blank line, and a block that a caller never fills in takes no space. A
 * description that a translator wrote over several lines is broken again here, so the line breaks are theirs
 * and not ours.
 */
public final class Lore {

    private static final String CRUMB_INDENT = "   ";
    private static final String TEXT_INDENT = "   ";
    private static final String DESCRIPTION_GLYPH = " ✎ ";
    private static final String DETAILS_GLYPH = " ≡ ";
    private static final String ROW_GLYPH = " ▪ ";
    private static final String STATUS_GLYPH = " • ";
    private static final String ACTION_GLYPH = " → ";

    private final Theme theme;
    private final List<List<Component>> blocks = new ArrayList<>();
    private List<Component> current = new ArrayList<>();

    private Lore(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** A new lore for one tile. */
    public static Lore of(Theme theme) {
        return new Lore(theme);
    }

    /** The category or type line that sits under the title. */
    public Lore crumb(Component crumb) {
        Objects.requireNonNull(crumb, "crumb");
        line(Component.text(CRUMB_INDENT).append(crumb.colorIfAbsent(theme.colour("crumb"))));
        return block();
    }

    /** The description block: the header, then one line for each line the catalog wrote. */
    public Lore description(Component header, Component text) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(text, "text");
        header(DESCRIPTION_GLYPH, header);
        for (Component sentence : split(text)) {
            line(Component.text(TEXT_INDENT).append(sentence.colorIfAbsent(theme.colour("subtext"))));
        }
        return block();
    }

    /** The details header. Call {@link #row} or {@link #status} after it. */
    public Lore details(Component header) {
        Objects.requireNonNull(header, "header");
        header(DETAILS_GLYPH, header);
        return this;
    }

    /** One fact: a label in the muted colour, then its value. */
    public Lore row(Component label, Component value) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        line(Component.text(ROW_GLYPH, theme.colour("icon"))
                .append(label.colorIfAbsent(theme.colour("muted")))
                .append(Component.text(" "))
                .append(value.colorIfAbsent(theme.colour("value"))));
        return this;
    }

    /** One state: the same shape as a fact, and the value is a coloured word rather than a glyph. */
    public Lore status(Component label, Component value) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        line(Component.text(STATUS_GLYPH, theme.colour("icon"))
                .append(label.colorIfAbsent(theme.colour("muted")))
                .append(Component.text(" "))
                .append(value));
        return this;
    }

    /** The click line, which closes the tile and names one action. */
    public Lore action(Component action) {
        Objects.requireNonNull(action, "action");
        block();
        line(Component.text(ACTION_GLYPH, theme.colour("dim")).append(action.colorIfAbsent(theme.colour("subtext"))));
        return block();
    }

    /** The lore as one component with a newline between the lines, which the item builder splits. */
    public Component build() {
        block();
        Component out = Component.empty();
        boolean first = true;
        for (List<Component> lines : blocks) {
            if (!first) {
                out = out.append(Component.newline()).append(Component.text(Tiles.PADDING));
            }
            for (int index = 0; index < lines.size(); index++) {
                if (!first || index > 0) {
                    out = out.append(Component.newline());
                }
                out = out.append(lines.get(index)).append(Component.text(Tiles.PADDING));
            }
            first = false;
        }
        return out;
    }

    private void header(String glyph, Component header) {
        line(Component.text(glyph, theme.colour("icon")).append(header.colorIfAbsent(theme.colour("info"))));
    }

    private void line(Component line) {
        current.add(line);
    }

    private Lore block() {
        if (!current.isEmpty()) {
            blocks.add(current);
            current = new ArrayList<>();
        }
        return this;
    }

    /** One component per line of a catalog entry that was written over several lines. */
    private static List<Component> split(Component text) {
        List<Component> lines = new ArrayList<>();
        for (String line : Text.serialize(text).split("\n", -1)) {
            lines.add(Text.mini(line));
        }
        return lines;
    }
}
