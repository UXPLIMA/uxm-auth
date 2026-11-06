package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * The connections waiting at the dialog before the world. They have no player yet, so the server does not find
 * them by id, and whatever staff or a bulletin does to an account must reach them here. A port, because the
 * dialog lives with the screens and the ones that reach it with the hold and the staff.
 */
public interface DialogWaits {

    /** The address of the connection waiting under this id, as an account records it; empty when none waits. */
    Optional<String> address(UUID connection);

    /** Whether a connection waits under this name, in any case. */
    boolean waitsAs(String name);

    /** Lets the connection waiting under this id into the world, the moment kept for it; whether one waited. */
    boolean letIn(UUID connection, AuthSettings.Moment moment);

    /** Sends the connection waiting under this id away with this reason, in its language; whether one waited. */
    boolean sendAway(UUID connection, MessageKey reason);
}
