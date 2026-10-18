package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.uxplima.auth.domain.network.Bulletin;

/**
 * The visits of the proxy's waiting world, by player, so a bulletin from staff on any server reaches the one it
 * names: staff letting an account in let its waiting player out of the world.
 */
public final class LimboVisits implements LimboVisit.Reach, Consumer<Bulletin> {

    private final Map<UUID, LimboVisit> visits = new ConcurrentHashMap<>();
    private final BiFunction<UUID, String, CompletableFuture<Boolean>> admit;

    /** @param admit lets the waiting connection in as forced, off the thread that asks */
    public LimboVisits(BiFunction<UUID, String, CompletableFuture<Boolean>> admit) {
        this.admit = Objects.requireNonNull(admit, "admit");
    }

    @Override
    public void arrived(UUID id, LimboVisit visit) {
        Objects.requireNonNull(visit, "visit");
        visits.compute(Objects.requireNonNull(id, "id"), (key, before) -> visit);
    }

    @Override
    public void left(UUID id, LimboVisit visit) {
        Objects.requireNonNull(visit, "visit");
        visits.compute(Objects.requireNonNull(id, "id"), (key, before) -> before == visit ? null : before);
    }

    @Override
    public CompletableFuture<Boolean> admit(UUID id, String address) {
        return admit.apply(Objects.requireNonNull(id, "id"), Objects.requireNonNull(address, "address"));
    }

    @Override
    public void accept(Bulletin bulletin) {
        if (Objects.requireNonNull(bulletin, "bulletin") instanceof Bulletin.LetIn letIn) {
            LimboVisit visit = visits.get(letIn.account());
            if (visit != null) {
                var unused = visit.letIn();
            }
        }
    }
}
