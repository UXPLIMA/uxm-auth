package com.uxplima.auth.adapter.outbound.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.uxplima.auth.testing.CountryDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The country file answers the country of an address, and an address it does not know answers none. */
final class CountryFileTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("an address inside a range reads that range's country; one outside reads none")
    void readsTheCountry() throws Exception {
        Path file = CountryDatabase.write(
                folder.resolve(CountryFile.NAME), Map.of("203.0.113.0/24", "TR", "198.51.100.0/24", "DE"));

        try (CountryFile countries = CountryFile.open(file).orElseThrow()) {
            assertThat(countries.of("203.0.113.7")).contains("TR");
            assertThat(countries.of("198.51.100.200")).contains("DE");
            assertThat(countries.of("192.0.2.1")).isEmpty();
            assertThat(countries.of("unknown")).isEmpty();
            assertThat(countries.of("2001:db8::1")).isEmpty();
        }
    }

    @Test
    @DisplayName("no file, or a file that is no country database, opens nothing")
    void noFileOpensNothing() throws Exception {
        assertThat(CountryFile.open(folder.resolve(CountryFile.NAME))).isEmpty();

        Path broken = Files.writeString(folder.resolve("broken.mmdb"), "not a database");
        assertThat(CountryFile.open(broken)).isEmpty();
    }
}
