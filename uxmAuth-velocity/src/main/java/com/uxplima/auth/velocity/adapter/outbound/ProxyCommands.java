package com.uxplima.auth.velocity.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.domain.state.AuthMethod;
import com.velocitypowered.api.proxy.Player;

/**
 * The commands an operator wants after a login, run on the proxy: the {@code [console]} and {@code [player]}
 * lines of the {@code registered} and {@code logged-in} lists, once per login, however many servers the player
 * visits after it. The other lines of those lists are what a server shows, and the proxy leaves them alone.
 */
public final class ProxyCommands {

    /** Runs a command on the proxy. */
    public interface Runner {
        void console(String command);

        void player(Player player, String command);
    }

    /** A command line: its verb, the modifiers in braces the proxy does not keep, and the command. */
    private static final Pattern COMMAND = Pattern.compile("\\s*\\[(console|player)]\\s*(?:\\{[^}]*}\\s*)?/?(.+)");

    private final Supplier<AuthSettings.Effects> effects;
    private final Function<UUID, Optional<Player>> online;
    private final Runner runner;

    /** @param online the player on the proxy under an id, when there is one */
    public ProxyCommands(
            Supplier<AuthSettings.Effects> effects, Function<UUID, Optional<Player>> online, Runner runner) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.online = Objects.requireNonNull(online, "online");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    /** The player logged in at the proxy, this way; a registration runs the registered list instead. */
    public void loggedIn(UUID id, AuthMethod method) {
        Objects.requireNonNull(method, "method");
        online.apply(Objects.requireNonNull(id, "id")).ifPresent(player -> {
            AuthSettings.Moment moment =
                    method == AuthMethod.REGISTERED ? AuthSettings.Moment.REGISTERED : AuthSettings.Moment.LOGGED_IN;
            for (String line : effects.get().of(moment)) {
                run(player, line);
            }
        });
    }

    private void run(Player player, String line) {
        Matcher command = COMMAND.matcher(line);
        if (!command.matches()) {
            return;
        }
        String filled = command.group(2)
                .strip()
                .replace("%player_name%", player.getUsername())
                .replace("%player_uuid%", player.getUniqueId().toString());
        if (command.group(1).equals("console")) {
            runner.console(filled);
        } else {
            runner.player(player, filled);
        }
    }
}
