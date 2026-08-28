package com.uxplima.template.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.template.domain.ExampleName;

/**
 * The outbound port through which a use case reaches storage. The application layer names this interface,
 * and an adapter implements it. That direction is the whole point: storage depends on the application, the
 * application does not depend on storage, and a test drives a use case with a fake.
 */
public interface ExampleRepository {

    /** Every example that {@code owner} holds, in insertion order. */
    List<ExampleName> findAll(UUID owner);

    /** Whether {@code owner} already holds {@code name}. */
    boolean exists(UUID owner, ExampleName name);

    /** Store {@code name} for {@code owner}. The caller has already checked the invariants. */
    void save(UUID owner, ExampleName name);

    /** Remove {@code name} from {@code owner}. Does nothing when it is absent. */
    void delete(UUID owner, ExampleName name);
}
