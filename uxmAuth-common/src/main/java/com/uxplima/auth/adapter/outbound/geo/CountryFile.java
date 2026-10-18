package com.uxplima.auth.adapter.outbound.geo;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import com.uxplima.auth.domain.protection.AddressKey;
import org.jspecify.annotations.Nullable;

/** The GeoLite2 country file, open for reading: the country an address is in, by its two-letter code. */
public final class CountryFile implements Closeable {

    /** The file's name in the data folder, as MaxMind names it. */
    public static final String NAME = "GeoLite2-Country.mmdb";

    /** The part of a record the rule reads. */
    public record Found(@Nullable Country country) {
        @MaxMindDbConstructor
        public Found(@MaxMindDbParameter(name = "country") @Nullable Country country) {
            this.country = country;
        }
    }

    /** A country, by the code ISO gives it. */
    public record Country(@Nullable String isoCode) {
        @MaxMindDbConstructor
        public Country(@MaxMindDbParameter(name = "iso_code") @Nullable String isoCode) {
            this.isoCode = isoCode;
        }
    }

    private final Reader reader;

    private CountryFile(Reader reader) {
        this.reader = reader;
    }

    /** The file opened, or nothing when there is none or it is not a country database. */
    public static Optional<CountryFile> open(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CountryFile(new Reader(file.toFile())));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** The country of the address, in upper case, or nothing when the file does not know it. */
    public Optional<String> of(String address) {
        Objects.requireNonNull(address, "address");
        if (!AddressKey.isAddress(address)) {
            return Optional.empty();
        }
        try {
            Found found = reader.get(InetAddress.ofLiteral(address), Found.class);
            return Optional.ofNullable(found)
                    .map(Found::country)
                    .map(Country::isoCode)
                    .map(code -> code.toUpperCase(Locale.ROOT));
        } catch (IOException | RuntimeException unknown) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
