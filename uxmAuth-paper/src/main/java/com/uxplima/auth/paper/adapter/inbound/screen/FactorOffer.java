package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.concurrent.CompletableFuture;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/** Asks the code of the second step, and takes it. */
public interface FactorOffer {

    /** Asks nothing and takes nothing, for a part built while no second step is asked. */
    FactorOffer NONE = new FactorOffer() {
        @Override
        public void offer(Audience reader, @Nullable Component reason) {}

        @Override
        public CompletableFuture<Void> answer(Guest guest, String code) {
            return CompletableFuture.completedFuture(null);
        }
    };

    /** Shows the code screen, with the reason on top. */
    void offer(Audience reader, @Nullable Component reason);

    /** One code for the second step, answering when the guest has been shown the outcome. */
    CompletableFuture<Void> answer(Guest guest, String code);
}
