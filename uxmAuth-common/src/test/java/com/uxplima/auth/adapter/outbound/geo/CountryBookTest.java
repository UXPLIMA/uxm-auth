package com.uxplima.auth.adapter.outbound.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import com.uxplima.auth.testing.CountryDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The country file the door asks, opened again whenever a new one lands in the folder. */
final class CountryBookTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("with no file the book is not ready; a file that lands is read at the next reload")
    void aFileThatLandsIsRead() throws Exception {
        try (CountryBook book = new CountryBook(folder)) {
            book.reload();
            assertThat(book.ready()).isFalse();
            assertThat(book.of("203.0.113.7")).isEmpty();

            CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "TR"));
            book.reload();
            assertThat(book.ready()).isTrue();
            assertThat(book.of("203.0.113.7")).contains("TR");

            Path fetched = CountryDatabase.write(folder.resolve("fetched.part"), Map.of("203.0.113.0/24", "DE"));
            java.nio.file.Files.move(
                    fetched,
                    folder.resolve(CountryFile.NAME),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            assertThat(book.of("203.0.113.7")).contains("TR");
            book.reload();
            assertThat(book.of("203.0.113.7")).contains("DE");
        }
    }

    @Test
    @DisplayName("a lookup that meets a reload is answered by the new file, never as an unknown address")
    void aLookupDuringAReloadIsAnswered() throws Exception {
        CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "TR"));
        try (CountryBook book = new CountryBook(folder)) {
            book.reload();
            java.util.concurrent.atomic.AtomicInteger unanswered = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
            java.util.concurrent.ExecutorService lookups = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                for (int i = 0; i < 4; i++) {
                    lookups.execute(() -> {
                        while (running.get()) {
                            if (book.of("203.0.113.7").isEmpty()) {
                                unanswered.incrementAndGet();
                            }
                        }
                    });
                }
                for (int i = 0; i < 300; i++) {
                    book.reload();
                }
            } finally {
                running.set(false);
                lookups.shutdown();
                assertThat(lookups.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
                        .isTrue();
            }

            assertThat(unanswered).hasValue(0);
        }
    }

    @Test
    @DisplayName("a file that cannot be read at a reload leaves the open one answering, and the reload says so")
    void anUnreadableFileKeepsTheOpenOne() throws Exception {
        CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "TR"));
        try (CountryBook book = new CountryBook(folder)) {
            book.reload();
            Path broken = folder.resolve("broken.part");
            java.nio.file.Files.writeString(broken, "not a country database");
            java.nio.file.Files.move(
                    broken,
                    folder.resolve(CountryFile.NAME),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            org.assertj.core.api.Assertions.assertThatThrownBy(book::reload)
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining(CountryFile.NAME);
            assertThat(book.ready()).isTrue();
            assertThat(book.of("203.0.113.7")).contains("TR");

            java.nio.file.Files.delete(folder.resolve(CountryFile.NAME));
            book.reload();
            assertThat(book.ready()).as("a file taken away is let go").isFalse();
        }
    }

    @Test
    @DisplayName("a closed file says it is closed rather than that it does not know the address")
    void aClosedFileSaysSo() throws Exception {
        CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "TR"));
        CountryFile file = CountryFile.open(folder.resolve(CountryFile.NAME)).orElseThrow();
        file.close();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> file.of("203.0.113.7"))
                .isInstanceOf(CountryFile.Closed.class);
    }
}
