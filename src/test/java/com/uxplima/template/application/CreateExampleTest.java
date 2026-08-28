package com.uxplima.template.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.template.domain.ExampleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A use case is driven with a fake and no server, which is the reason the layers are separated. */
final class CreateExampleTest {

    private final UUID owner = UUID.randomUUID();
    private final FakeExampleRepository repository = new FakeExampleRepository();

    @Test
    @DisplayName("the first example is created and the fact carries the owner and the name")
    void createsTheFirstExample() {
        CreateExample useCase = new CreateExample(repository, 3);

        var outcome = useCase.run(owner, new ExampleName("first"));

        assertThat(outcome).isInstanceOfSatisfying(CreateExample.Outcome.Created.class, created -> {
            assertThat(created.fact().owner()).isEqualTo(owner);
            assertThat(created.fact().name().value()).isEqualTo("first");
        });
        assertThat(repository.findAll(owner)).hasSize(1);
    }

    @Test
    @DisplayName("the same name twice is refused, and nothing is stored the second time")
    void refusesADuplicate() {
        CreateExample useCase = new CreateExample(repository, 3);
        useCase.run(owner, new ExampleName("same"));

        var outcome = useCase.run(owner, new ExampleName("same"));

        assertThat(outcome).isInstanceOf(CreateExample.Outcome.AlreadyExists.class);
        assertThat(repository.findAll(owner)).hasSize(1);
    }

    @Test
    @DisplayName("the limit is refused at the limit, not one past it")
    void refusesAtTheLimit() {
        CreateExample useCase = new CreateExample(repository, 2);
        useCase.run(owner, new ExampleName("one"));
        useCase.run(owner, new ExampleName("two"));

        var outcome = useCase.run(owner, new ExampleName("three"));

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        CreateExample.Outcome.LimitReached.class,
                        reached -> assertThat(reached.limit()).isEqualTo(2));
        assertThat(repository.findAll(owner)).hasSize(2);
    }

    @Test
    @DisplayName("one owner's limit does not consume another owner's")
    void limitsArePerOwner() {
        CreateExample useCase = new CreateExample(repository, 1);
        useCase.run(owner, new ExampleName("mine"));

        var outcome = useCase.run(UUID.randomUUID(), new ExampleName("theirs"));

        assertThat(outcome).isInstanceOf(CreateExample.Outcome.Created.class);
    }
}
