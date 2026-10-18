package com.uxplima.auth.adapter.outbound.geo;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.application.port.Countries;
import org.jspecify.annotations.Nullable;

/**
 * The country file in the data folder, as the door asks it. A reload opens the file that is there now and
 * closes the one before, so a fetched file is read without a restart.
 */
public final class CountryBook implements Countries, Closeable {

    private final Path folder;
    private final AtomicReference<@Nullable CountryFile> open = new AtomicReference<>();

    public CountryBook(Path folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /** Opens the file that is in the folder now, or none when there is none. */
    public void reload() throws IOException {
        CountryFile before = open.getAndSet(
                CountryFile.open(folder.resolve(CountryFile.NAME)).orElse(null));
        if (before != null) {
            before.close();
        }
    }

    @Override
    public boolean ready() {
        return open.get() != null;
    }

    @Override
    public Optional<String> of(String address) {
        Objects.requireNonNull(address, "address");
        CountryFile file = open.get();
        return file == null ? Optional.empty() : file.of(address);
    }

    @Override
    public void close() throws IOException {
        CountryFile before = open.getAndSet(null);
        if (before != null) {
            before.close();
        }
    }
}
