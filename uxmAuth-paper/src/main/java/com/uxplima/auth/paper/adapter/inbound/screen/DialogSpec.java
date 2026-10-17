package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

/**
 * What one screen shows, before it becomes a Paper dialog: its title, its lines, its fields and its buttons.
 *
 * <p>A test reads this whole. Paper builds the dialog itself only on a running server, so everything the
 * player sees is decided here and the step to Paper's types is a translation with no choices in it.
 */
public record DialogSpec(
        Component title, List<Component> body, List<Input> inputs, List<Button> buttons, boolean canClose) {

    public DialogSpec {
        Objects.requireNonNull(title, "title");
        body = List.copyOf(Objects.requireNonNull(body, "body"));
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        buttons = List.copyOf(Objects.requireNonNull(buttons, "buttons"));
    }

    /** One field the player fills in, named by the key its answer comes back under. */
    public sealed interface Input {
        String key();

        Component label();
    }

    /** A line of text, at most this long. */
    public record TextInput(String key, Component label, int maxLength) implements Input {
        public TextInput {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
        }
    }

    /** A box the player ticks. */
    public record Toggle(String key, Component label) implements Input {
        public Toggle {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
        }
    }

    /** A button, and the action its press comes back as. */
    public record Button(Component label, String action) {
        public Button {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }
}
