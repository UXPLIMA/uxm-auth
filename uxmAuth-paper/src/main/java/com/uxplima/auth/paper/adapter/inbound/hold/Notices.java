package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.AuthSettings.Moment;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What the door found out about a connection before it had a player, told once the player stands in the world:
 * why their last try was refused, or that their account moved. Some are for a waiting player, some only for one
 * who has logged in, so a stranger under a name is never told where its account went.
 */
public final class Notices {

    /** One thing to tell, what to do once it was told, and the effects list it runs for the player then. */
    public record Notice(MessageKey key, List<TagResolver> resolvers, Runnable read, Optional<Moment> moment) {
        public Notice {
            Objects.requireNonNull(key, "key");
            resolvers = List.copyOf(Objects.requireNonNull(resolvers, "resolvers"));
            Objects.requireNonNull(read, "read");
            Objects.requireNonNull(moment, "moment");
        }

        /** A notice with no list of its own. */
        public Notice(MessageKey key, List<TagResolver> resolvers, Runnable read) {
            this(key, resolvers, read, Optional.empty());
        }
    }

    private record Pending(Notice notice, boolean afterLogin) {}

    private final Messages messages;
    private final MomentRunner moments;
    private final Map<UUID, List<Pending>> pending = new ConcurrentHashMap<>();

    public Notices(Messages messages) {
        this(messages, (player, moment) -> {});
    }

    /** @param moments runs the list a notice names, once its player read it */
    public Notices(Messages messages, MomentRunner moments) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.moments = Objects.requireNonNull(moments, "moments");
    }

    /** Told as soon as the player stands in the world, logged in or not. */
    public void whileWaiting(UUID id, Notice notice) {
        add(id, new Pending(notice, false));
    }

    /** Told once the player has logged in. */
    public void onceIn(UUID id, Notice notice) {
        add(id, new Pending(notice, true));
    }

    /** Tells the player what is theirs to read now, on their own thread. */
    public void deliver(Player player, boolean loggedIn) {
        Objects.requireNonNull(player, "player");
        List<Pending> due = new ArrayList<>();
        pending.computeIfPresent(player.getUniqueId(), (id, list) -> {
            List<Pending> left = new ArrayList<>();
            for (Pending each : list) {
                (loggedIn || !each.afterLogin() ? due : left).add(each);
            }
            return left.isEmpty() ? null : List.copyOf(left);
        });
        for (Pending each : due) {
            messages.send(player, each.notice().key(), each.notice().resolvers().toArray(TagResolver[]::new));
            each.notice().read().run();
            each.notice().moment().ifPresent(moment -> moments.fire(player, moment));
        }
    }

    /** The player left; what was waiting for them goes. */
    public void forget(UUID id) {
        pending.remove(Objects.requireNonNull(id, "id"));
    }

    private void add(UUID id, Pending one) {
        Objects.requireNonNull(id, "id");
        pending.compute(id, (key, list) -> {
            List<Pending> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
            next.add(one);
            return List.copyOf(next);
        });
    }
}
