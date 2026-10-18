package com.uxplima.auth.adapter.outbound.geo;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.maxmind.db.ClosedDatabaseException;
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

    /** The file was closed under a lookup, because a newer one took its place. */
    public static final class Closed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Closed(Throwable cause) {
            super("The country file was closed under the lookup", cause);
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
            return Optional.of(read(file));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** The file opened, or why a file that is there could not be: not a country database, or not readable now. */
    static CountryFile read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try {
            return new CountryFile(new Reader(file.toFile()));
        } catch (RuntimeException unreadable) {
            throw new IOException(file.getFileName() + " is not a country database", unreadable);
        } catch (IOException unreadable) {
            throw new IOException(file.getFileName() + " could not be read: " + unreadable.getMessage(), unreadable);
        }
    }

    /**
     * The country of the address, in upper case, or nothing when the file does not know it.
     *
     * @throws Closed when the file was closed under the lookup, so the caller can ask the file that replaced it
     */
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
        } catch (ClosedDatabaseException swapped) {
            throw new Closed(swapped);
        } catch (IOException | RuntimeException unknown) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
