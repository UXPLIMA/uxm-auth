package com.uxplima.auth.adapter.outbound.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.auth.testing.CountryDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The book is opened at the start and opened again whenever a fetch brought a new file. */
final class CountryUpkeepTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("a fetch that brings a file is read at once; one that brings nothing leaves the book as it was")
    void aFetchedFileIsReadAtOnce() throws Exception {
        List<String> said = new ArrayList<>();
        try (CountryBook book = new CountryBook(folder)) {
            CountryUpkeep upkeep = new CountryUpkeep(
                    () -> {
                        try {
                            CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "TR"));
                        } catch (java.io.IOException unwritable) {
                            throw new java.io.UncheckedIOException(unwritable);
                        }
                        return CountryDownload.Outcome.FETCHED;
                    },
                    book,
                    said::add);
            new CountryUpkeep(() -> CountryDownload.Outcome.FAILED, book, said::add).run();
            assertThat(book.ready()).isFalse();

            upkeep.run();

            assertThat(book.of("203.0.113.7")).contains("TR");
            assertThat(said).isEmpty();
        }
    }

    @Test
    @DisplayName("a fetched file replaces the one the book had open")
    void aFetchedFileReplacesTheOpenOne() throws Exception {
        CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "DE"));
        try (CountryBook book = new CountryBook(folder)) {
            book.reload();
            // A fetch lands its file under a new name and moves it over the old one, as the download does.
            Path fetched = CountryDatabase.write(folder.resolve("fetched.part"), Map.of("203.0.113.0/24", "TR"));
            java.nio.file.Files.move(
                    fetched,
                    folder.resolve(CountryFile.NAME),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            new CountryUpkeep(() -> CountryDownload.Outcome.FETCHED, book, message -> {}).run();

            assertThat(book.of("203.0.113.7")).contains("TR");
        }
    }

    @Test
    @DisplayName("the first run opens a file the operator dropped in by hand, with no licence to fetch one")
    void theFirstRunOpensAFileDroppedIn() throws Exception {
        CountryDatabase.write(folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "DE"));
        try (CountryBook book = new CountryBook(folder)) {
            new CountryUpkeep(() -> CountryDownload.Outcome.NO_LICENCE, book, message -> {}).run();

            assertThat(book.of("203.0.113.7")).contains("DE");
        }
    }
}
