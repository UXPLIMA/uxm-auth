package com.uxplima.auth.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The reason a connection is turned away at the door, on the screen it is kicked to. A server and the proxy
 * turn players away for the same reasons, so they read them from one place.
 *
 * <p>A connection turned away has no player yet, so the words are read in the default language.
 */
public final class DoorWords {

    private final Messages messages;
    private final TimeWords times;

    public DoorWords(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.times = new TimeWords(messages);
    }

    /** Why the identity decision turned the connection away; {@code movedTo} names a moved account. */
    public Component refusal(Refusal why, Optional<PlayerName> movedTo) {
        Objects.requireNonNull(why, "why");
        Objects.requireNonNull(movedTo, "movedTo");
        Audience nobody = Audience.empty();
        return switch (why) {
            case LOOKUP_FAILED -> messages.render(nobody, Keys.KICK_LOOKUP_FAILED);
            case PAID_NAME ->
                movedTo.map(to -> messages.render(
                                nobody, Keys.KICK_PAID_NAME_MOVED, Text.placeholder("name", to.value())))
                        .orElseGet(() -> messages.render(nobody, Keys.KICK_PAID_NAME));
            case PREMIUM_ADDRESS -> messages.render(nobody, Keys.KICK_PREMIUM_ADDRESS);
            case NAME_DISPUTED -> messages.render(nobody, Keys.KICK_NAME_DISPUTED);
            case NAME_KEPT -> messages.render(nobody, Keys.KICK_NAME_KEPT);
        };
    }

    /** Why a second connection under the name of a player already online is turned away. */
    public Component alreadyOnline() {
        return messages.render(Audience.empty(), Keys.KICK_ALREADY_ONLINE);
    }

    /** Why a server behind the proxy turned away a player the network could not vouch for. */
    public Component network() {
        return messages.render(Audience.empty(), Keys.KICK_NETWORK_CLAIM);
    }

    /** Why the operator's rules refuse the name. */
    public Component name(NameVerdict verdict) {
        Objects.requireNonNull(verdict, "verdict");
        Audience nobody = Audience.empty();
        return switch (verdict) {
            case NameVerdict.TooShort tooShort ->
                messages.render(
                        nobody, Keys.KICK_NAME_TOO_SHORT, Text.placeholder("min", String.valueOf(tooShort.minimum())));
            case NameVerdict.TooLong tooLong ->
                messages.render(
                        nobody, Keys.KICK_NAME_TOO_LONG, Text.placeholder("max", String.valueOf(tooLong.maximum())));
            case NameVerdict.BadCharacters bad -> messages.render(nobody, Keys.KICK_NAME_CHARACTERS);
            case NameVerdict.Blocked blocked ->
                messages.render(nobody, Keys.KICK_NAME_BLOCKED, Text.placeholder("word", blocked.word()));
            case NameVerdict.WrongCase wrongCase ->
                messages.render(
                        nobody,
                        Keys.KICK_NAME_CASE,
                        Text.placeholder("name", wrongCase.registered().value()));
            case NameVerdict.Accepted accepted -> throw new IllegalArgumentException("an accepted name is no refusal");
        };
    }

    /** Why one of the door's rules turned the connection away, read before anybody knows its language. */
    public Component admission(Admission.Refused refused) {
        Objects.requireNonNull(refused, "refused");
        Audience nobody = Audience.empty();
        List<TagResolver> values = new ArrayList<>();
        refused.values().forEach((name, value) -> values.add(Text.placeholder(name, value)));
        values.add(times.placeholder(nobody, refused.lasts()));
        return messages.render(nobody, refused.key(), values.toArray(TagResolver[]::new));
    }
}
