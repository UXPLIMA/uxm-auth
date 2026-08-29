package com.uxplima.template.adapter.outbound.style;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands the style tokens of a message template into the MiniMessage the client is sent.
 *
 * <p>A catalog line names a role and never a colour: {@code <accent>}, {@code <value>}, {@code <body>}. This
 * class turns each one into the colour {@link Theme} holds for that role. Three tokens insert text of their
 * own instead of wrapping it:
 *
 * <ul>
 *   <li>{@code <tag:'HOME'>} the category prefix a chat line opens with: the word in small capitals and bold,
 *       coloured by what the category is about, then the dim separator;
 *   <li>{@code <etag:'ERROR'>} the same prefix in the failure colour, whichever feature raised the line;
 *   <li>{@code <h:'REWARDS'>} the bold accent header a lore block opens with.
 * </ul>
 *
 * <p>The label of a prefix lives in the template rather than in this class, because it is a word that a
 * player reads and a translator has to be able to move it.
 *
 * <p>Anything that is not a token is copied through, so an operator keeps every MiniMessage tag: a click, a
 * hover, a font, and the placeholders the plugin fills in.
 */
public final class StyleTokens {

    /** A tag with a label: {@code <tag:'HOME'>}, {@code <tag:HOME>} or {@code <tag:"HOME">}. */
    private static final Pattern LABELLED =
            Pattern.compile("<(tag|etag|h):(?:'([^']*)'|\"([^\"]*)\"|([^>]*))>", Pattern.CASE_INSENSITIVE);

    /** A colour token, opening or closing: {@code <accent>}, {@code </accent>}. */
    private static final Pattern COLOUR = Pattern.compile("</?([a-z]+)>");

    private StyleTokens() {}

    /**
     * {@code template} with every token replaced by MiniMessage.
     *
     * @param smallCaps whether the label of a prefix is written in small capitals, which follows the language
     *     the line is rendered for
     */
    public static String expand(String template, Theme theme, boolean smallCaps) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(theme, "theme");
        return colours(labels(template, theme, smallCaps), theme);
    }

    private static String labels(String template, Theme theme, boolean smallCaps) {
        Matcher matcher = LABELLED.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            String label = firstOf(matcher.group(2), matcher.group(3), matcher.group(4));
            if (label.isBlank()) {
                throw new IllegalArgumentException("A <" + token + "> token needs a label: " + template);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(expanded(token, label, theme, smallCaps)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String expanded(String token, String label, Theme theme, boolean smallCaps) {
        String text = smallCaps ? SmallCaps.of(label) : label;
        return switch (token) {
            case "tag" -> prefix(text, theme.hex(theme.categoryRole(label)), theme);
            case "etag" -> prefix(text, theme.hex("bad"), theme);
            default -> bold(text, theme.hex("accent"));
        };
    }

    /** The word, then one ordinary space, then the separator. The gap before the body comes from the catalog. */
    private static String prefix(String label, String hex, Theme theme) {
        return bold(label, hex) + " <color:" + theme.hex("dim") + ">" + theme.separator() + "</color>";
    }

    private static String bold(String text, String hex) {
        return "<b><color:" + hex + ">" + text + "</color></b>";
    }

    private static String colours(String template, Theme theme) {
        Matcher matcher = COLOUR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String role = matcher.group(1);
            if (!theme.hasColour(role)) {
                continue;
            }
            boolean closing = matcher.group().charAt(1) == '/';
            String replacement = closing ? "</color>" : "<color:" + theme.hex(role) + ">";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String firstOf(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
