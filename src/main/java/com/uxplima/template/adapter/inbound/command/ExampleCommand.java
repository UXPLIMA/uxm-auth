package com.uxplima.template.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.uxplima.template.application.CreateExample;
import com.uxplima.template.application.message.Keys;
import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.template.domain.ExampleName;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The inbound adapter for the example feature. An adapter translates: it turns a command into a call on a
 * use case, and it turns the outcome back into a message. It holds no rule of its own, which is why the
 * rules can be tested without a server.
 *
 * <p>The command name here is the default. An operator renames, aliases, or disables it in
 * {@code commands.conf}, and nothing in this class changes.
 */
@FromConfig(value = ExampleCommand.KEY, fallbackName = "example", description = "Manage your examples")
public final class ExampleCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "example";

    private final CreateExample createExample;
    private final ExampleRepository repository;
    private final Messages messages;

    public ExampleCommand(CreateExample createExample, ExampleRepository repository, Messages messages) {
        this.createExample = Objects.requireNonNull(createExample, "createExample");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("create")
    @Permission("uxmtemplate.example.create")
    @PlayerOnly
    public void create(Sender sender, @Arg("name") String rawName) {
        Player player = sender.player().orElseThrow();

        ExampleName name;
        try {
            name = new ExampleName(rawName);
        } catch (IllegalArgumentException invalidName) {
            messages.send(player, Keys.INVALID_NAME);
            return;
        }

        switch (createExample.run(player.getUniqueId(), name)) {
            case CreateExample.Outcome.Created created ->
                messages.send(
                        player,
                        Keys.EXAMPLE_CREATED,
                        Text.placeholder("name", created.fact().name().value()));
            case CreateExample.Outcome.AlreadyExists exists ->
                messages.send(
                        player,
                        Keys.EXAMPLE_EXISTS,
                        Text.placeholder("name", exists.name().value()));
            case CreateExample.Outcome.LimitReached limit ->
                messages.send(player, Keys.EXAMPLE_LIMIT, Text.placeholder("limit", String.valueOf(limit.limit())));
        }
    }

    @Subcommand("list")
    @Permission("uxmtemplate.example.list")
    @PlayerOnly
    public void list(Sender sender) {
        Player player = sender.player().orElseThrow();
        List<ExampleName> names = repository.findAll(player.getUniqueId());

        if (names.isEmpty()) {
            messages.send(player, Keys.EXAMPLE_LIST_EMPTY);
            return;
        }
        String joined = names.stream().map(ExampleName::value).collect(Collectors.joining(", "));
        messages.send(player, Keys.EXAMPLE_LIST, Text.placeholder("names", joined));
    }
}
