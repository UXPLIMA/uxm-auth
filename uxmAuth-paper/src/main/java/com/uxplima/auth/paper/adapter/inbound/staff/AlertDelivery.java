package com.uxplima.auth.paper.adapter.inbound.staff;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Tells every alert to the staff on this server who hold the alerts node, each on their own thread, in their own
 * language. The node is read there too, so a holder who lost it meanwhile hears nothing.
 */
public final class AlertDelivery implements Consumer<Bulletin> {

    /** The node that hears alerts. */
    public static final String PERMISSION = "uxmauth.admin.alerts";

    private final Supplier<? extends Collection<? extends Player>> online;
    private final Scheduler scheduler;
    private final Messages messages;
    private final TimeWords times;

    /** @param online every player on this server */
    public AlertDelivery(
            Supplier<? extends Collection<? extends Player>> online, Scheduler scheduler, Messages messages) {
        this.online = Objects.requireNonNull(online, "online");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    @Override
    public void accept(Bulletin bulletin) {
        if (!(Objects.requireNonNull(bulletin, "bulletin") instanceof Bulletin.Alert alert)) {
            return;
        }
        for (Player player : online.get()) {
            scheduler.entity(player, () -> {
                if (player.hasPermission(PERMISSION)) {
                    messages.send(player, key(alert), words(player, alert));
                }
            });
        }
    }

    private static Keys key(Bulletin.Alert alert) {
        return switch (alert) {
            case Bulletin.FailedLogin failed -> Keys.ALERT_FAILED_LOGIN;
            case Bulletin.Banned banned -> Keys.ALERT_BANNED;
            case Bulletin.SharedAddress shared -> Keys.ALERT_SHARED_ADDRESS;
            case Bulletin.StaffActed acted -> Keys.ALERT_STAFF_ACTED;
        };
    }

    private TagResolver[] words(Player reader, Bulletin.Alert alert) {
        return switch (alert) {
            case Bulletin.FailedLogin failed -> new TagResolver[] {Text.placeholder("address", failed.address())};
            case Bulletin.Banned banned ->
                new TagResolver[] {
                    Text.placeholder("address", banned.address()), times.placeholder(reader, banned.length())
                };
            case Bulletin.SharedAddress shared ->
                new TagResolver[] {
                    Text.placeholder("name", shared.name().value()),
                    Text.placeholder("address", shared.address()),
                    Text.placeholder("count", String.valueOf(shared.accounts()))
                };
            case Bulletin.StaffActed acted ->
                new TagResolver[] {
                    acted.actor()
                            .map(actor -> Text.placeholder("actor", actor.value()))
                            .orElseGet(
                                    () -> Placeholder.component("actor", messages.render(reader, Keys.ALERT_CONSOLE))),
                    Text.placeholder("action", acted.action()),
                    Text.placeholder("target", acted.target())
                };
        };
    }
}
