package com.uxplima.auth.adapter.outbound.geo;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps the country book up to date: fetches the file when it is due, and opens the book again when a new file
 * came, or when the book has none open yet. The platform runs it off the server thread, at the start and then
 * every few hours.
 */
public final class CountryUpkeep implements Runnable {

    private final Supplier<CountryDownload.Outcome> refresh;
    private final CountryBook book;
    private final Consumer<String> console;

    public CountryUpkeep(Supplier<CountryDownload.Outcome> refresh, CountryBook book, Consumer<String> console) {
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.book = Objects.requireNonNull(book, "book");
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public void run() {
        CountryDownload.Outcome outcome = refresh.get();
        if (outcome != CountryDownload.Outcome.FETCHED && book.ready()) {
            return;
        }
        try {
            book.reload();
        } catch (IOException unreadable) {
            console.accept("Could not open the country file: " + unreadable.getMessage());
        }
    }
}
