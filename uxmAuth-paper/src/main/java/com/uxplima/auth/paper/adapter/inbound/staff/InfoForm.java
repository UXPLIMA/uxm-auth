package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The info view on Bedrock: one form whose text is the lines the console reads, and a button for each other
 * account on its addresses, which reads that account the same way.
 */
public final class InfoForm implements InfoWindow {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockScreen forms;
    private final Predicate<UUID> bedrock;
    private final Messages messages;
    private final Scheduler scheduler;
    private final BiConsumer<Player, String> openAlt;

    /** @param openAlt reads the account under a name for the player who tapped it */
    public InfoForm(
            BedrockScreen forms,
            Predicate<UUID> bedrock,
            Messages messages,
            Scheduler scheduler,
            BiConsumer<Player, String> openAlt) {
        this.forms = Objects.requireNonNull(forms, "forms");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.openAlt = Objects.requireNonNull(openAlt, "openAlt");
    }

    @Override
    public boolean show(Player viewer, AccountInfo.View view, List<Component> lines) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(lines, "lines");
        if (!bedrock.test(viewer.getUniqueId())) {
            return false;
        }
        List<String> alts = view.alts().stream().map(PlayerName::value).toList();
        String title = PLAIN.serialize(messages.render(
                viewer,
                Keys.MENU_INFO_TITLE,
                Text.placeholder("info_name", view.account().name().value())));
        String content = String.join("\n", lines.stream().map(PLAIN::serialize).toList());
        forms.sendSimpleForm(
                viewer,
                title,
                content,
                alts.stream().map(name -> new BedrockButton(name, null)).toList(),
                index -> {
                    if (index >= 0 && index < alts.size()) {
                        scheduler.entity(viewer, () -> openAlt.accept(viewer, alts.get(index)));
                    }
                });
        return true;
    }
}
