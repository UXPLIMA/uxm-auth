package com.uxplima.template.adapter.outbound.style;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.text.format.TextColor;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * The palette of one plugin, read from {@code theme.conf}.
 *
 * <p>This is the only class that holds a colour. A message file names a role ({@code <accent>},
 * {@code <value>}), never a hex code, so a customer who wants a red server edits one file and every message,
 * every menu and every item follows. It also keeps the palette from drifting: a key that says {@code <body>}
 * is the same white in all sixteen plugins.
 *
 * <p>A role that the file does not name keeps the shipped default, so an operator may write three lines
 * instead of seventeen, and a new role added later cannot break an old file.
 */
public final class Theme {

    /** The roles and their shipped colours. The order is the order of the shipped file. */
    private static final Map<String, String> DEFAULT_COLOURS = defaultColours();

    /** The categories whose prefix is not the accent colour, mapped to the role that colours them. */
    private static final Map<String, String> DEFAULT_CATEGORIES =
            Map.of("error", "bad", "money", "good", "shop", "good", "event", "event");

    private static final String DEFAULT_SEPARATOR = "▶";

    /**
     * The languages that are written in small capitals unless the file says otherwise. Small capitals exist
     * for the Latin alphabet only, so the safe default is English and nothing else: a language with letters
     * of its own keeps them until somebody who reads that language decides otherwise. A key in
     * {@code theme.conf} overrides one language, and the languages it leaves out keep this default.
     */
    private static final Set<String> DEFAULT_SMALL_CAPS = Set.of("en");

    private final Map<String, TextColor> colours;
    private final Map<String, String> categories;
    private final String separator;
    private final Set<String> smallCapsLanguages;

    private Theme(
            Map<String, TextColor> colours,
            Map<String, String> categories,
            String separator,
            Set<String> smallCapsLanguages) {
        this.colours = Map.copyOf(colours);
        this.categories = Map.copyOf(categories);
        this.separator = separator;
        this.smallCapsLanguages = Set.copyOf(smallCapsLanguages);
    }

    /** The shipped palette, used when {@code theme.conf} is absent or empty. */
    public static Theme defaults() {
        Map<String, TextColor> colours = new LinkedHashMap<>();
        DEFAULT_COLOURS.forEach((role, hex) -> colours.put(role, parse(hex)));
        return new Theme(colours, DEFAULT_CATEGORIES, DEFAULT_SEPARATOR, DEFAULT_SMALL_CAPS);
    }

    /** The palette in {@code node}, with the shipped default behind every value the file leaves out. */
    public static Theme from(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        Map<String, TextColor> colours = new LinkedHashMap<>();
        for (var entry : DEFAULT_COLOURS.entrySet()) {
            String hex = node.node("colours", entry.getKey()).getString(entry.getValue());
            colours.put(entry.getKey(), parse(hex));
        }
        Map<String, String> categories = new HashMap<>(DEFAULT_CATEGORIES);
        for (var child : node.node("prefix", "categories").childrenMap().entrySet()) {
            String role = child.getValue().getString();
            if (role != null) {
                categories.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), role);
            }
        }
        String separator = node.node("prefix", "separator").getString(DEFAULT_SEPARATOR);
        Map<Object, ? extends ConfigurationNode> languages =
                node.node("small-caps").childrenMap();
        Set<String> smallCaps = new java.util.HashSet<>(DEFAULT_SMALL_CAPS);
        for (var child : languages.entrySet()) {
            String language = String.valueOf(child.getKey()).toLowerCase(Locale.ROOT);
            if (child.getValue().getBoolean()) {
                smallCaps.add(language);
            } else {
                smallCaps.remove(language);
            }
        }
        return new Theme(colours, categories, separator, smallCaps);
    }

    /** The colour of {@code role}, or the body colour when the role is unknown to this theme. */
    public TextColor colour(String role) {
        Objects.requireNonNull(role, "role");
        TextColor found = colours.get(role);
        return found != null ? found : colours.get("body");
    }

    /** Whether {@code role} is a colour of this theme, which is what makes a token a token. */
    public boolean hasColour(String role) {
        Objects.requireNonNull(role, "role");
        return colours.containsKey(role);
    }

    /** The hex of {@code role} as MiniMessage writes it, for example {@code #38b6ff}. */
    public String hex(String role) {
        return colour(role).asHexString().toLowerCase(Locale.ROOT);
    }

    /** The colour of the category prefix {@code label} carries. Anything not listed reads in the accent. */
    public String categoryRole(String label) {
        Objects.requireNonNull(label, "label");
        return categories.getOrDefault(label.toLowerCase(Locale.ROOT), "accent");
    }

    /** The glyph between the category word and the message. */
    public String separator() {
        return separator;
    }

    /** Whether {@code locale} is written in small capitals. */
    public boolean smallCaps(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return smallCapsLanguages.contains(locale.getLanguage().toLowerCase(Locale.ROOT));
    }

    private static TextColor parse(String hex) {
        TextColor parsed = TextColor.fromHexString(hex);
        if (parsed == null) {
            throw new IllegalArgumentException("theme.conf holds an invalid colour: " + hex);
        }
        return parsed;
    }

    private static Map<String, String> defaultColours() {
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("accent", "#38b6ff");
        colours.put("body", "#ffffff");
        colours.put("subtext", "#dde8f0");
        colours.put("muted", "#93a4b3");
        colours.put("dim", "#6b7886");
        colours.put("icon", "#8a93a1");
        colours.put("crumb", "#565f6b");
        colours.put("value", "#8fd9ff");
        colours.put("good", "#5be38c");
        colours.put("bad", "#ff6b6b");
        colours.put("warn", "#ffc93c");
        colours.put("money", "#ffc93c");
        colours.put("level", "#ffc93c");
        colours.put("cta", "#ffc93c");
        colours.put("info", "#4fd6e8");
        colours.put("rank", "#b68cff");
        colours.put("event", "#ff8fd0");
        return Map.copyOf(colours);
    }
}
