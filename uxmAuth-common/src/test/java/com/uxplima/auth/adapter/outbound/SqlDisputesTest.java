package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.auth.application.port.Disputes;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A name both a cracked account and a paid owner want, held until staff decide. */
final class SqlDisputesTest {

    @TempDir
    Path folder;

    private Database database;
    private SqlDisputes disputes;

    @BeforeEach
    void open() {
        database = AuthSchema.migrate(Database.builder()
                .sqlite(folder.resolve("auth.db"))
                .poolName("uxmauth-test")
                .build());
        disputes = new SqlDisputes(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("a dispute staff settle for the cracked holder is kept, and reads as kept")
    void aDisputeIsKept() {
        UUID mojang = UUID.randomUUID();
        Instant at = Instant.ofEpochMilli(1_760_000_000_000L);
        disputes.raise(new PlayerName("Notch"), mojang, at);

        disputes.keep(new PlayerName("NOTCH"));

        assertThat(disputes.of(new PlayerName("notch"))).contains(new Disputes.Dispute(mojang, at, true));
    }

    @Test
    @DisplayName("a dispute is raised once per name, read in any case, and gone once resolved")
    void aDisputeIsRaisedReadAndResolved() {
        UUID mojang = UUID.randomUUID();
        Instant at = Instant.ofEpochMilli(1_760_000_000_000L);

        disputes.raise(new PlayerName("Notch"), mojang, at);
        disputes.raise(new PlayerName("NOTCH"), UUID.randomUUID(), at);

        assertThat(disputes.of(new PlayerName("notch"))).contains(new Disputes.Dispute(mojang, at));
        disputes.resolve(new PlayerName("Notch"));
        assertThat(disputes.of(new PlayerName("Notch"))).isEmpty();
    }
}
