package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Two servers that start together on one database take turns at the migrations: on MySQL, MariaDB and PostgreSQL
 * the second waits for the first's lock, and the lock is given back however the migrations end.
 */
final class SchemaLockTest {

    private final List<String> done = new CopyOnWriteArrayList<>();
    private final List<String> names = new CopyOnWriteArrayList<>();

    /** A database that records each statement it is sent, and answers a lock with {@code granted}. */
    private Database recording(Dialect dialect, int granted) throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(asked -> {
            String sql = asked.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            doAnswer(named -> names.add(named.getArgument(1))).when(statement).setString(anyInt(), anyString());
            doAnswer(keyed -> names.add(Long.toHexString(keyed.getArgument(1))))
                    .when(statement)
                    .setLong(anyInt(), anyLong());
            ResultSet answer = mock(ResultSet.class);
            when(answer.next()).thenReturn(true);
            when(answer.getInt(1)).thenReturn(granted);
            when(statement.executeQuery()).thenAnswer(run -> {
                done.add(sql);
                return answer;
            });
            return statement;
        });
        return Database.adopt(source, dialect);
    }

    @Test
    @DisplayName("on MySQL and MariaDB the migrations run inside a named lock, given back after")
    void mysqlTakesANamedLock() throws SQLException {
        SchemaLock.holding(recording(Dialect.MYSQL, 1), () -> done.add("migrations"));

        assertThat(done).containsExactly("SELECT GET_LOCK(?, ?)", "migrations", "SELECT RELEASE_LOCK(?)");
    }

    @Test
    @DisplayName("on PostgreSQL the migrations run inside an advisory lock, given back after")
    void postgresTakesAnAdvisoryLock() throws SQLException {
        SchemaLock.holding(recording(Dialect.POSTGRES, 1), () -> done.add("migrations"));

        assertThat(done).containsExactly("SELECT pg_advisory_lock(?)", "migrations", "SELECT pg_advisory_unlock(?)");
    }

    @Test
    @DisplayName("a migration that fails still gives the lock back, and its failure is the one reported")
    void aFailureGivesTheLockBack() throws SQLException {
        Database database = recording(Dialect.MYSQL, 1);

        assertThatThrownBy(() -> SchemaLock.holding(database, () -> {
                    throw new IllegalStateException("migration 9 failed");
                }))
                .hasMessage("migration 9 failed");
        assertThat(done).containsExactly("SELECT GET_LOCK(?, ?)", "SELECT RELEASE_LOCK(?)");
    }

    @Test
    @DisplayName("a lock another server keeps past the wait is reported, and no migration runs")
    void aLockNotGrantedIsReported() throws SQLException {
        Database database = recording(Dialect.MYSQL, 0);

        assertThatThrownBy(() -> SchemaLock.holding(database, () -> done.add("migrations")))
                .hasMessageContaining("another server");
        assertThat(done).doesNotContain("migrations");
    }

    @Test
    @DisplayName("a file database belongs to one server, so its migrations run with no lock")
    void aFileDatabaseTakesNoLock() throws SQLException {
        SchemaLock.holding(recording(Dialect.SQLITE, 1), () -> done.add("migrations"));
        SchemaLock.holding(recording(Dialect.H2, 1), () -> done.add("again"));

        assertThat(done).containsExactly("migrations", "again");
    }

    @Test
    @DisplayName("an import takes a lock of its own, so it never waits on another server's migrations")
    void anImportHasItsOwnLock() throws SQLException {
        SchemaLock.holding(recording(Dialect.MYSQL, 1), SchemaLock.IMPORT, () -> done.add("import"));
        SchemaLock.holding(recording(Dialect.POSTGRES, 1), SchemaLock.IMPORT, () -> done.add("import"));
        SchemaLock.holding(recording(Dialect.MYSQL, 1), () -> done.add("migrations"));
        SchemaLock.holding(recording(Dialect.POSTGRES, 1), () -> done.add("migrations"));

        assertThat(names)
                .containsExactly(
                        "uxmauth_import", "uxmauth_import",
                        "75786d6175746802", "75786d6175746802",
                        "uxmauth_schema", "uxmauth_schema",
                        "75786d6175746801", "75786d6175746801");
    }

    @Test
    @DisplayName("a lock another server keeps names what it was waiting for")
    void aLockNotGrantedNamesItsWork() throws SQLException {
        Database database = recording(Dialect.MYSQL, 0);

        assertThatThrownBy(() -> SchemaLock.holding(database, SchemaLock.IMPORT, () -> done.add("import")))
                .hasMessageContaining("import lock");
    }

    @Test
    @DisplayName("only a database servers share opens a pool for the lock")
    void onlyASharedDatabaseIsLocked() {
        assertThat(SchemaLock.shared(Dialect.MYSQL)).isTrue();
        assertThat(SchemaLock.shared(Dialect.POSTGRES)).isTrue();
        assertThat(SchemaLock.shared(Dialect.SQLITE)).isFalse();
        assertThat(SchemaLock.shared(Dialect.H2)).isFalse();
        assertThat(SchemaLock.shared(Dialect.GENERIC)).isFalse();
    }
}
