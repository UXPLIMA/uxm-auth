package com.uxplima.template.adapter.outbound.style;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Applies the whole style layer to a message catalog, once, when the plugin loads and again on a reload.
 *
 * <p>The pass is two pure steps over a template: the letters ({@link Typography}), then the tokens
 * ({@link StyleTokens}). Doing it at load rather than at render costs nothing per message, cannot be
 * forgotten at a call site, and can be tested with no server.
 *
 * <p>Every key is written into the styled catalog for every language, with the fallback of the source catalog
 * already applied. That is what makes the English default compiled into a key look exactly like the file: the
 * default is a template like any other, and it goes through the same two steps.
 *
 * <p>uxmLib 0.10.0 lets {@code Messages} carry a base resolver, and this class still does not become one.
 * Only half of the pass can: {@link StyleTokens} expands tags, but {@link Typography} rewrites literal text,
 * and a {@code TagResolver} cannot do that. Splitting one pass across load and render would buy nothing,
 * because a reload rebuilds the whole catalog anyway. See /home/uxm-briefs/UXMLIB-FOLLOWUP.md.
 */
public final class Styler {

    private volatile Theme theme;

    public Styler(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** The palette this styler paints with. Menus need it for the parts of a tile that are not text. */
    public Theme theme() {
        return theme;
    }

    /**
     * Take a new palette.
     *
     * <p>Every holder of this styler sees it at once, which is the point: a menu asks for the theme each time
     * it builds a tile, so an operator who edits {@code theme.conf} and reloads sees the menus repaint with
     * the chat. Building a second styler instead would repaint the chat and leave every menu on the old
     * colours, which is the half repainted screen the canon forbids.
     */
    public void reload(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /** One template, styled for {@code locale}. */
    public String apply(String template, Locale locale) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(locale, "locale");
        boolean smallCaps = theme.smallCaps(locale);
        return StyleTokens.expand(Typography.apply(template, smallCaps), theme, smallCaps);
    }

    /**
     * {@code source} with every template styled.
     *
     * @param keys every key the plugin can show, which is the key enum
     * @param files what each language file holds, flattened to {@code a.b.c -> template}
     */
    public MessageCatalog style(
            MessageCatalog source,
            Iterable<? extends MessageKey> keys,
            Map<Locale, ? extends Map<String, String>> files,
            Locale defaultLocale) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Set<Locale> locales = new LinkedHashSet<>(files.keySet());
        locales.add(defaultLocale);
        Map<Locale, Map<String, String>> styled = new HashMap<>();
        for (Locale locale : locales) {
            Map<String, String> templates = new LinkedHashMap<>();
            for (MessageKey key : keys) {
                templates.put(key.path(), apply(source.template(key, locale), locale));
            }
            // The file second, so a path the enum does not know is styled too: a plugin can build a key at
            // run time, and a line like that is the one line an operator wrote themselves.
            Map<String, String> file = files.get(locale);
            if (file != null) {
                for (var entry : file.entrySet()) {
                    templates.put(entry.getKey(), apply(entry.getValue(), locale));
                }
            }
            styled.put(locale, templates);
        }
        return new MessageCatalog(styled, defaultLocale);
    }
}
