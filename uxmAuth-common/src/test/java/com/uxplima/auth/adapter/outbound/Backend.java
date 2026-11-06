package com.uxplima.auth.adapter.outbound;

import java.nio.file.Path;
import java.util.UUID;

import com.uxplima.uxmlib.storage.sql.Database;

/** The databases a storage test runs on without a server: a SQLite file and an H2 in memory, both migrated. */
enum Backend {
    SQLITE,
    H2;

    /** A fresh database with every migration applied. */
    Database open(Path folder) {
        Database database =
                switch (this) {
                    case SQLITE ->
                        Database.builder()
                                .sqlite(folder.resolve(UUID.randomUUID() + ".db"))
                                .build();
                    case H2 ->
                        Database.builder()
                                .jdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                                .build();
                };
        return AuthSchema.migrate(database);
    }
}
