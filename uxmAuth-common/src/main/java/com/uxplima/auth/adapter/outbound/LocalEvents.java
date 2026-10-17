package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.network.Bulletin;

/** The bulletins of a member with no Redis behind it: it hears what it publishes, at once, and nothing else. */
public final class LocalEvents implements NetworkEvents {

    private final List<Consumer<Bulletin>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(Bulletin bulletin) {
        Objects.requireNonNull(bulletin, "bulletin");
        for (Consumer<Bulletin> listener : listeners) {
            listener.accept(bulletin);
        }
    }

    @Override
    public void listen(Consumer<Bulletin> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
