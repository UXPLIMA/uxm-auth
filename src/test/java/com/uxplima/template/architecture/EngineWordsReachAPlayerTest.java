package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.template.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.language.Languages;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The windows the engine draws itself say words in this plugin, in every language it ships.
 *
 * <p>The confirm prompt, the page arrows and the acknowledgement a cancelled prompt sends are drawn by the
 * library, which holds no words and asks this plugin's catalogue for them. Nothing here had written those
 * keys, so a player who pressed escape out of an editor prompt read the characters
 * {@code gui.input.cancelled} in chat, and the owner found it by playing.
 *
 * <p>uxmLib now carries the words underneath this plugin's own files, and this test is that arriving. It runs
 * the load this plugin actually runs, the style pass included, because the style pass builds the catalogue it
 * returns out of the key enum and the files: a line that reaches neither is a line that is simply not there,
 * however well the library holds it.
 */
final class EngineWordsReachAPlayerTest {

    private static final Path MESSAGES = Path.of("src", "main", "resources", "messages");

    private static final Locale TR = Locale.forLanguageTag("tr");

    /**
     * Four of the engine's keys, one from each window it draws: a chat line, a button, an arrow, and the question a
     * Bedrock form asks when one tile does more than one thing. uxm-plots found that question reading as its keys.
     */
    private static final List<String> ASKED =
            List.of("gui.input.cancelled", "gui.confirm.yes", "gui.page.next", "gui.gesture.title");

    @Test
    @DisplayName("a key of the engine reads as words in every language this plugin ships")
    void theEngineKeysAreWords() {
        MessageCatalog catalog = styled();

        for (Locale locale : List.of(Locale.ENGLISH, TR)) {
            for (String path : ASKED) {
                String words = plain(catalog.template(MessageKey.of(path, path), locale));
                assertThat(words)
                        .describedAs("%s in %s", path, locale.toLanguageTag())
                        .isNotBlank()
                        .doesNotContain(path);
            }
        }
    }

    @Test
    @DisplayName("the two languages do not answer with the same sentence, which would mean one of them fell back")
    void eachLanguageAnswersInItsOwn() {
        MessageCatalog catalog = styled();

        assertThat(plain(catalog.template(MessageKey.of(ASKED.getFirst(), ASKED.getFirst()), TR)))
                .isNotEqualTo(
                        plain(catalog.template(MessageKey.of(ASKED.getFirst(), ASKED.getFirst()), Locale.ENGLISH)));
    }

    /** The catalogue this plugin builds at boot: the files, painted with the theme, with the keys it knows. */
    private static MessageCatalog styled() {
        Languages languages = Languages.load(MESSAGES, Locale.ENGLISH);
        Styler styler = new Styler(Theme.defaults());
        return styler.style(languages.catalog(), List.of(Keys.values()), languages.entries(), Locale.ENGLISH);
    }

    private static String plain(String template) {
        return PlainTextComponentSerializer.plainText().serialize(Text.mini(template));
    }
}
