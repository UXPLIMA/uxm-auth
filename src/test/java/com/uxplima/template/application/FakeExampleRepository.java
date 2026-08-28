package com.uxplima.template.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.template.domain.ExampleName;

/**
 * A hand-written fake, not a mock. A fake behaves, so a test reads as a story about the use case rather
 * than a list of stubbed calls, and it does not break when the port grows a method.
 */
final class FakeExampleRepository implements ExampleRepository {

    private final Map<UUID, List<ExampleName>> byOwner = new LinkedHashMap<>();

    @Override
    public List<ExampleName> findAll(UUID owner) {
        return List.copyOf(byOwner.getOrDefault(owner, List.of()));
    }

    @Override
    public boolean exists(UUID owner, ExampleName name) {
        return findAll(owner).contains(name);
    }

    @Override
    public void save(UUID owner, ExampleName name) {
        byOwner.computeIfAbsent(owner, key -> new ArrayList<>()).add(name);
    }

    @Override
    public void delete(UUID owner, ExampleName name) {
        byOwner.getOrDefault(owner, new ArrayList<>()).remove(name);
    }
}
