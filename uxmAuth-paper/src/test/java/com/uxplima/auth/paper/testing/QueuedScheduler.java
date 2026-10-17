package com.uxplima.auth.paper.testing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * Runs a player's and a region's work at once, and holds async work until the test runs it, in either order.
 *
 * <p>Two async tasks on a real server run on two threads and finish in any order. Running the queue last
 * first is how a test makes the bad order happen every time.
 */
public final class QueuedScheduler implements Scheduler {

    private final DirectScheduler direct = new DirectScheduler();
    private final Deque<Runnable> async = new ArrayDeque<>();

    /** Run every queued async task, newest first, until none is left. */
    public void runNewestFirst() {
        while (!async.isEmpty()) {
            async.pollLast().run();
        }
    }

    /** Run the oldest queued async task alone. */
    public void runOldest() {
        Runnable task = async.pollFirst();
        if (task != null) {
            task.run();
        }
    }

    /** Run every queued async task, oldest first, until none is left. */
    public void runOldestFirst() {
        while (!async.isEmpty()) {
            async.pollFirst().run();
        }
    }

    @Override
    public TaskHandle async(Runnable task) {
        async.addLast(task);
        return direct.global(() -> {});
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        return async(task);
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return direct.asyncTimer(delay, period, task);
    }

    @Override
    public TaskHandle global(Runnable task) {
        return direct.global(task);
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        return direct.globalLater(delay, task);
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return direct.globalTimer(delay, period, task);
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        return direct.region(location, task);
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        return direct.regionLater(location, delay, task);
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return direct.regionTimer(location, delay, period, task);
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        return direct.entity(entity, task);
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        return direct.entityLater(entity, delay, task);
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return direct.entityTimer(entity, delay, period, task);
    }
}
