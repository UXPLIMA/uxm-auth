package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.ScreenOffer;
import com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /captcha <code>}: the answer to a code on a map in the hand, or typed instead of on the screen. The right
 * code brings back the screen the captcha stood before; any other answer spends the code and shows a new one.
 */
@FromConfig(value = CaptchaCommand.KEY, fallbackName = "captcha", description = "Answer the captcha")
public final class CaptchaCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "captcha";

    private final CaptchaScreen captcha;
    private final Function<UUID, Optional<AuthState>> states;
    private final ScreenOffer next;
    private final Messages messages;

    /**
     * @param states the session state of each connection
     * @param next what shows a waiting player the screen of their state, once the captcha is out of the way
     */
    public CaptchaCommand(
            CaptchaScreen captcha, Function<UUID, Optional<AuthState>> states, ScreenOffer next, Messages messages) {
        this.captcha = Objects.requireNonNull(captcha, "captcha");
        this.states = Objects.requireNonNull(states, "states");
        this.next = Objects.requireNonNull(next, "next");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.captcha")
    @PlayerOnly
    public void answer(Sender sender, @Arg("code") String code) {
        answer((Player) sender.bukkit(), code);
    }

    /** The whole command, on the player's own thread. */
    void answer(Player player, String code) {
        UUID id = player.getUniqueId();
        if (!captcha.waiting(id)) {
            messages.send(player, Keys.CAPTCHA_NOTHING);
            return;
        }
        if (!captcha.solve(id, code)) {
            captcha.offer(player, id, messages.render(player, Keys.CAPTCHA_WRONG));
            return;
        }
        messages.send(player, Keys.CAPTCHA_SOLVED);
        states.apply(id).ifPresent(state -> next.offer(player, state));
    }
}
