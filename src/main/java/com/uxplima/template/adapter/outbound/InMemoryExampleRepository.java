package com.uxplima.template.adapter.outbound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.template.domain.ExampleName;

/**
 * The in-memory implementation of {@link ExampleRepository}. It is the starting adapter: replace it with a
 * database-backed one through {@code uxmlib-storage} when the data must survive a restart.
 *
 * <p>State keyed by player is a {@link ConcurrentHashMap} that holds an immutable value, and it is mutated
 * only through {@code compute}. That is the one pattern this plugin uses for shared mutable state, because
 * a read-modify-write on a plain map races under Folia.
 */
public final class InMemoryExampleRepository implements ExampleRepository {

    private final Map<UUID, List<ExampleName>> byOwner = new ConcurrentHashMap<>();

    @Override
    public List<ExampleName> findAll(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return byOwner.getOrDefault(owner, List.of());
    }

    @Override
    public boolean exists(UUID owner, ExampleName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        return findAll(owner).contains(name);
    }

    @Override
    public void save(UUID owner, ExampleName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        byOwner.compute(owner, (key, current) -> {
            List<ExampleName> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            if (!next.contains(name)) {
                next.add(name);
            }
            return Collections.unmodifiableList(next);
        });
    }

    @Override
    public void delete(UUID owner, ExampleName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        byOwner.computeIfPresent(owner, (key, current) -> {
            List<ExampleName> next = new ArrayList<>(current);
            next.remove(name);
            return next.isEmpty() ? null : Collections.unmodifiableList(next);
        });
    }
}
