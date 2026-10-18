package com.uxplima.auth.application;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.domain.password.Password;

/** Wipes the passwords a use case was handed once its answer is known, on every path. */
public final class Wiping {

    private Wiping() {}

    public static <T> CompletableFuture<T> after(Supplier<CompletableFuture<T>> work, Password... passwords) {
        CompletableFuture<T> answer;
        try {
            answer = work.get();
        } catch (RuntimeException failed) {
            wipe(passwords);
            throw failed;
        }
        return answer.whenComplete((result, failure) -> wipe(passwords));
    }

    private static void wipe(Password... passwords) {
        for (Password password : passwords) {
            password.close();
        }
    }
}
