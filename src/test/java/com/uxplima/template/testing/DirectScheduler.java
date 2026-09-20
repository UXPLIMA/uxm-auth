package com.uxplima.template.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A scheduler that runs everything at once, on the calling thread.
 *
 * <p>A test wants the order the plugin asked for, not the order a server would choose. Running a task where
 * it was scheduled makes the assertion follow the call, and a timer is held back so a test can tick it by
 * hand rather than wait for wall-clock time to pass.
 */
public final class DirectScheduler implements Scheduler {

    /** Every repeating task that was started, in the order it was started. */
    public final List<Consumer<TaskHandle>> timers = new ArrayList<>();

    /** How many tasks were handed over in total. */
    public int scheduled;

    /** Run every repeating task once. */
    public void tick() {
        for (Consumer<TaskHandle> timer : List.copyOf(timers)) {
            timer.accept(handle());
        }
    }

    @Override
    public TaskHandle global(Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timer(task);
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timer(task);
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timer(task);
    }

    @Override
    public TaskHandle async(Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return timer(task);
    }

    private TaskHandle run(Runnable task) {
        scheduled++;
        task.run();
        return handle();
    }

    private TaskHandle timer(Consumer<TaskHandle> task) {
        scheduled++;
        timers.add(task);
        return handle();
    }

    private static TaskHandle handle() {
        return new TaskHandle() {

            private boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }
}
