package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;

/**
 * Every screen as a native Bedrock form, through uxmLib's form sender.
 *
 * <p>A screen with one submit becomes a custom form whose fields are the screen's fields; submitting it is
 * that button's press and closing it is leaving the screen. A screen of many buttons, the pad, becomes a list
 * of buttons, and a tap is that button's press.
 */
public final class BedrockForms implements ScreenShower {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int ONE_SUBMIT = 2;

    private final BedrockScreen forms;
    private final FormAnswers answers;

    public BedrockForms(BedrockScreen forms, FormAnswers answers) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.answers = Objects.requireNonNull(answers, "answers");
    }

    @Override
    public void show(Audience who, DialogSpec spec) {
        if (!(who instanceof Player player)) {
            return;
        }
        String title = PLAIN.serialize(spec.title());
        String content =
                String.join("\n", spec.body().stream().map(PLAIN::serialize).toList());
        if (spec.buttons().size() > ONE_SUBMIT) {
            List<BedrockButton> buttons = spec.buttons().stream()
                    .map(button -> new BedrockButton(PLAIN.serialize(button.label()), null))
                    .toList();
            forms.sendSimpleForm(player, title, content, buttons, index -> press(player, spec, index));
            return;
        }
        String submit = spec.buttons().isEmpty()
                ? AuthScreens.CANCEL
                : spec.buttons().get(0).action();
        forms.sendCustomForm(
                player,
                title,
                content,
                widgets(spec),
                values -> answers.answer(player, submit, new Filled(values)),
                () -> answers.answer(player, AuthScreens.CANCEL, new Filled(Map.of())));
    }

    @Override
    public void close(Audience who) {
        // A Bedrock form closes itself on its answer; there is nothing left open to close.
    }

    private void press(Player player, DialogSpec spec, int index) {
        if (index >= 0 && index < spec.buttons().size()) {
            answers.answer(player, spec.buttons().get(index).action(), new Filled(Map.of()));
        }
    }

    private static List<BedrockWidget> widgets(DialogSpec spec) {
        List<BedrockWidget> widgets = new ArrayList<>();
        for (DialogSpec.Input input : spec.inputs()) {
            String label = plain(input.label());
            widgets.add(
                    switch (input) {
                        case DialogSpec.TextInput text -> new BedrockWidget.Input(text.key(), label, "", "");
                        case DialogSpec.Toggle toggle -> new BedrockWidget.Toggle(toggle.key(), label, false);
                    });
        }
        return widgets;
    }

    private static String plain(Component text) {
        return PLAIN.serialize(text);
    }

    /** What the form sent back, by field name; a toggle comes back as true or false. */
    private record Filled(Map<String, String> values) implements ScreenAnswers.Answer {
        @Override
        public @Nullable String text(String key) {
            return values.get(key);
        }

        @Override
        public boolean ticked(String key) {
            return Boolean.parseBoolean(values.get(key));
        }
    }
}
