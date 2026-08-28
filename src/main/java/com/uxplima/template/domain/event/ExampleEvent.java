package com.uxplima.template.domain.event;

import java.util.UUID;

import com.uxplima.template.domain.ExampleName;

/**
 * Everything that happens in this context, as one sealed family. A fact is a record: all of it is in its
 * components, two with the same components are the same thing, and none of it has behaviour to hide.
 *
 * <p>The adapter layer bridges these to Bukkit events. The domain never names a Bukkit type.
 */
public sealed interface ExampleEvent {

    /** An example was created for an owner. */
    record ExampleCreated(UUID owner, ExampleName name) implements ExampleEvent {}

    /** An example was deleted. */
    record ExampleDeleted(UUID owner, ExampleName name) implements ExampleEvent {}
}
