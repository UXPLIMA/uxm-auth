package com.uxplima.auth.adapter.outbound.geo;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.application.port.Countries;
import org.jspecify.annotations.Nullable;

/**
 * The country file in the data folder, as the door asks it. A reload opens the file that is there now and
 * closes the one before, so a fetched file is read without a restart; a file that cannot be read is not swapped in.
 */
public final class CountryBook implements Countries, Closeable {

    /** How often a lookup follows a swap before it gives up; a reload a few times inside one lookup is absurd. */
    private static final int TRIES = 8;

    private final Path folder;
    private final AtomicReference<@Nullable CountryFile> open = new AtomicReference<>();

    public CountryBook(Path folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /**
     * Opens the file that is in the folder now, or none when there is none. A file that is there and cannot be
     * read leaves the open one answering, and says why: a lookup is never left unanswered by a bad reload.
     */
    public void reload() throws IOException {
        Path file = folder.resolve(CountryFile.NAME);
        CountryFile now = Files.isRegularFile(file) ? CountryFile.read(file) : null;
        CountryFile before = open.getAndSet(now);
        if (before != null) {
            before.close();
        }
    }

    @Override
    public boolean ready() {
        return open.get() != null;
    }

    /**
     * A lookup that meets a reload finds the file it asked closed; the newer file is in place by then, so it is
     * asked instead, and the address is never answered as unknown because of the swap.
     */
    @Override
    public Optional<String> of(String address) {
        Objects.requireNonNull(address, "address");
        CountryFile file = open.get();
        for (int tried = 0; file != null && tried < TRIES; tried++) {
            try {
                return file.of(address);
            } catch (CountryFile.Closed swapped) {
                file = open.get();
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() throws IOException {
        CountryFile before = open.getAndSet(null);
        if (before != null) {
            before.close();
        }
    }
}
