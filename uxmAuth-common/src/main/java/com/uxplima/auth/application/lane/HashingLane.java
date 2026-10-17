package com.uxplima.auth.application.lane;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Where every hash and every check runs.
 *
 * <p>An Argon2id hash at the shipped numbers holds 64 MiB for about a tenth of a second. A hundred players
 * joining at once would ask for 6.4 GiB if each ran where it arrived. Here at most {@code maxThreads} run, the
 * rest wait in a queue of fixed size, and the one after that is refused at once so the player can be told to
 * try again rather than left hanging.
 *
 * <p>The threads are daemons, so a lane nobody closed never keeps a stopping server alive.
 */
public final class HashingLane implements AutoCloseable {

    private static final long IDLE_SECONDS = 30;

    private final ThreadPoolExecutor pool;

    public HashingLane(int coreThreads, int maxThreads, int queueCapacity) {
        if (coreThreads < 1 || maxThreads < coreThreads || queueCapacity < 1) {
            throw new IllegalArgumentException("a lane needs at least one thread, a maximum no lower than the core"
                    + " and room for one waiting task; got " + coreThreads + ", " + maxThreads + ", "
                    + queueCapacity);
        }
        this.pool = new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                IDLE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("uxmauth-hash-", 1).daemon(true).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Runs the work on the lane. A full or closed lane fails the future with {@link LaneFullException}. */
    public <T> CompletableFuture<T> submit(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        try {
            return CompletableFuture.supplyAsync(work, pool);
        } catch (RejectedExecutionException full) {
            return CompletableFuture.failedFuture(new LaneFullException());
        }
    }

    /** How many tasks wait for a thread now, for the performance view. */
    public int waiting() {
        return pool.getQueue().size();
    }

    /** How many tasks run now, for the performance view. */
    public int running() {
        return pool.getActiveCount();
    }

    /** Takes no more work and lets the work already taken finish. */
    @Override
    public void close() {
        pool.shutdown();
    }
}
