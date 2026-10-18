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
}
