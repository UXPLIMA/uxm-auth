package com.uxplima.template.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.template.domain.ExampleName;
import com.uxplima.template.domain.event.ExampleEvent;

/**
 * One use case: create an example for an owner. A use case orchestrates the domain through ports, and it
 * returns an outcome rather than throwing for a business rule. It names no Bukkit type, so a plain JUnit
 * test can drive it with a fake repository and no server.
 *
 * <p>This is the shape that every use case in a plugin takes. Replace it with the real one.
 */
public final class CreateExample {

    private final ExampleRepository repository;
    private final int limit;

    public CreateExample(ExampleRepository repository, int limit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got: " + limit);
        }
        this.limit = limit;
    }

    /** The outcome of the use case. A caller must handle every case, because the interface is sealed. */
    public sealed interface Outcome {

        /** The example was created, and the fact is carried for the caller to publish. */
        record Created(ExampleEvent.ExampleCreated fact) implements Outcome {}

        /** The owner already holds an example under that name. */
        record AlreadyExists(ExampleName name) implements Outcome {}

        /** The owner is at the limit. */
        record LimitReached(int limit) implements Outcome {}
    }

    public Outcome run(UUID owner, ExampleName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");

        if (repository.exists(owner, name)) {
            return new Outcome.AlreadyExists(name);
        }
        if (repository.findAll(owner).size() >= limit) {
            return new Outcome.LimitReached(limit);
        }

        repository.save(owner, name);
        return new Outcome.Created(new ExampleEvent.ExampleCreated(owner, name));
    }
}
