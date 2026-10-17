package com.uxplima.auth.paper.testing;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * Queues the work a server would run on a player's or a region's thread, for the test's own thread to run, and
 * runs async work where it was asked for.
 *
 * <p>MockBukkit refuses a kick or a potion effect from any thread but the one that started it, the way a real
 * server refuses them off the owning thread. A hash finishes on the lane, so its answer has to come back here.
 */
public final class MainThreadScheduler implements Scheduler {

    private final DirectScheduler timers = new DirectScheduler();
    private final Queue<Runnable> main = new ConcurrentLinkedQueue<>();

    /** Runs every queued task, and the tasks those queue, until none is left. */
    public void drain() {
        Runnable task;
        while ((task = main.poll()) != null) {
            task.run();
        }
    }

    /** Runs queued tasks until the future is done, for at most ten seconds. */
    public void settle(CompletableFuture<?> future) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone()) {
            drain();
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the answer did not come back within ten seconds");
            }
            Thread.onSpinWait();
        }
        drain();
        future.join();
    }

    /** Runs every repeating task once. */
    public void tick() {
        timers.tick();
    }

    private TaskHandle queue(Runnable task) {
        main.add(task);
        return timers.global(() -> {});
    }

    @Override
    public TaskHandle global(Runnable task) {
        return queue(task);
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        return queue(task);
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timers.globalTimer(delay, period, task);
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        return queue(task);
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        return queue(task);
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timers.regionTimer(location, delay, period, task);
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        return queue(task);
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        return queue(task);
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timers.entityTimer(entity, delay, period, task);
    }

    @Override
    public TaskHandle async(Runnable task) {
        return timers.async(task);
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        return timers.asyncLater(delay, task);
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timers.asyncTimer(delay, period, task);
    }
}
