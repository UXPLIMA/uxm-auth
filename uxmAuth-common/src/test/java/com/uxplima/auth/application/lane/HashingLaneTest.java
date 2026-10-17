package com.uxplima.auth.application.lane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Argon2id at 64 MiB a hash: a wave of joins must queue, not take the heap.
 *
 * <p>Every test has a time limit, because a lane that drops work leaves a future nobody completes, and a
 * test joining it would wait for ever instead of failing. The limit runs on its own thread because
 * {@code join} does not answer an interrupt.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class HashingLaneTest {

    @Test
    @DisplayName("no more hashes run at once than the lane allows")
    void neverRunsMoreThanTheMaximumAtOnce() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger highest = new AtomicInteger();
        List<CompletableFuture<Integer>> all = new ArrayList<>();
        try (HashingLane lane = new HashingLane(2, 4, 64)) {
            for (int i = 0; i < 40; i++) {
                all.add(lane.submit(() -> {
                    highest.accumulateAndGet(running.incrementAndGet(), Math::max);
                    sleep(20);
                    return running.decrementAndGet();
                }));
            }
            CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
        }

        assertThat(highest.get()).isBetween(1, 4);
    }

    @Test
    @DisplayName("work past the queue is refused at once, not waited for")
    void refusesPastTheQueue() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try (HashingLane lane = new HashingLane(1, 1, 1)) {
            CompletableFuture<Integer> blocking = lane.submit(() -> {
                started.countDown();
                await(release);
                return 1;
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Integer> queued = lane.submit(() -> 2);

            CompletableFuture<Integer> refused = lane.submit(() -> 3);

            assertThatThrownBy(refused::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(LaneFullException.class);
            release.countDown();
            assertThat(blocking.join()).isEqualTo(1);
            assertThat(queued.join()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("a closed lane takes no more work")
    void closeStopsAcceptingWork() {
        HashingLane lane = new HashingLane(1, 1, 1);
        lane.close();

        assertThatThrownBy(lane.submit(() -> 1)::join).hasCauseInstanceOf(LaneFullException.class);
    }

    @Test
    @DisplayName("the threads carry the plugin's name and never hold a server open")
    void threadsAreNamedDaemons() {
        try (HashingLane lane = new HashingLane(1, 1, 1)) {
            Thread worker = lane.submit(Thread::currentThread).join();

            assertThat(worker.getName()).startsWith("uxmauth-hash-");
            assertThat(worker.isDaemon()).isTrue();
        }
    }

    @Test
    @DisplayName("a failing hash fails its own future and leaves the lane working")
    void aFailureStaysInItsFuture() {
        try (HashingLane lane = new HashingLane(1, 1, 4)) {
            CompletableFuture<Integer> broken = lane.submit(() -> {
                throw new IllegalStateException("broken");
            });

            assertThatThrownBy(broken::join).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(lane.submit(() -> 7).join()).isEqualTo(7);
        }
    }

    @Test
    @DisplayName("sizes that cannot work are refused when the lane is built")
    void refusesImpossibleSizes() {
        assertThatThrownBy(() -> new HashingLane(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HashingLane(3, 2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HashingLane(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
