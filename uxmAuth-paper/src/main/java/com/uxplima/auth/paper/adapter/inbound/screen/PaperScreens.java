package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.List;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;

/**
 * Turns a {@link DialogSpec} into Paper's dialog and puts it in front of a player. A translation with no choice
 * in it: everything the player sees was decided in the spec.
 */
public final class PaperScreens implements ScreenShower {

    /** The namespace every button of this plugin's screens answers under. */
    public static final String NAMESPACE = "uxmauth";

    private static final int BUTTON_WIDTH = 150;

    @Override
    public void show(Audience who, DialogSpec spec) {
        who.showDialog(dialog(spec));
    }

    @Override
    public void close(Audience who) {
        who.closeDialog();
    }

    static Dialog dialog(DialogSpec spec) {
        DialogBase base = DialogBase.builder(spec.title())
                .canCloseWithEscape(spec.canClose())
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(spec.body().stream().map(DialogBody::plainMessage).toList())
                .inputs(spec.inputs().stream().map(PaperScreens::input).toList())
                .build();
        List<ActionButton> buttons =
                spec.buttons().stream().map(PaperScreens::button).toList();
        DialogType type = buttons.size() == 2
                ? DialogType.confirmation(buttons.get(0), buttons.get(1))
                : DialogType.multiAction(buttons).columns(spec.columns()).build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static DialogInput input(DialogSpec.Input input) {
        return switch (input) {
            case DialogSpec.TextInput text ->
                DialogInput.text(text.key(), text.label())
                        .maxLength(text.maxLength())
                        .build();
            case DialogSpec.Toggle toggle ->
                DialogInput.bool(toggle.key(), toggle.label()).build();
        };
    }

    private static ActionButton button(DialogSpec.Button button) {
        return ActionButton.builder(button.label())
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(Key.key(NAMESPACE, button.action()), null))
                .build();
    }
}
