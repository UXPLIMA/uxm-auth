package com.uxplima.auth.application.port;

import java.util.stream.Stream;

/** Another login plugin's accounts, read and never written. */
public interface ImportSource extends AutoCloseable {

    /** The name an import of this source is marked complete under. */
    String key();

    /** Every account the source holds, one at a time. The stream throws when the source fails. */
    Stream<ImportedAccount> read();

    @Override
    void close();
}
