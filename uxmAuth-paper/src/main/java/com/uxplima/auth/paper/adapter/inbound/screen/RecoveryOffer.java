package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.concurrent.CompletableFuture;

/** A forgotten password from the login screen: a code by mail, then the code and a new password. */
public interface RecoveryOffer {

    /** Mails nothing and takes nothing, for a part built without mail. */
    RecoveryOffer NONE = new RecoveryOffer() {
        @Override
        public CompletableFuture<Void> forgot(Guest guest) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> recover(Guest guest, String code, String fresh) {
            return CompletableFuture.completedFuture(null);
        }
    };

    /** "Forgot password" was pressed: a code goes to the linked address, answering once the guest is shown why. */
    CompletableFuture<Void> forgot(Guest guest);

    /** The code and a new password, answering once the guest is shown the outcome. */
    CompletableFuture<Void> recover(Guest guest, String code, String fresh);
}
