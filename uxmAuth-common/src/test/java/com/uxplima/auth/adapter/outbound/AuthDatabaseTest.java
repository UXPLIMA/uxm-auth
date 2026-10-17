package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every database the brief names opens with the driver this jar carries, whatever the platform ships. */
final class AuthDatabaseTest {

    @Test
    @DisplayName("each JDBC scheme names the driver this jar carries for it")
    void eachSchemeNamesItsDriver() {
        assertThat(AuthDatabase.driverOf("jdbc:sqlite:/srv/auth.db")).isEqualTo("org.sqlite.JDBC");
        assertThat(AuthDatabase.driverOf("jdbc:mariadb://db:3306/auth")).isEqualTo("org.mariadb.jdbc.Driver");
        assertThat(AuthDatabase.driverOf("jdbc:mysql://db:3306/auth")).isEqualTo("org.mariadb.jdbc.Driver");
        assertThat(AuthDatabase.driverOf("jdbc:postgresql://db:5432/auth")).isEqualTo("org.postgresql.Driver");
        assertThat(AuthDatabase.driverOf("jdbc:h2:./auth")).isEqualTo("org.h2.Driver");
    }

    @Test
    @DisplayName("a mysql address is opened by the MariaDB driver, which speaks MySQL, under a scheme it takes")
    void aMysqlAddressIsOpenedByMariadb() {
        assertThat(AuthDatabase.addressOf("jdbc:mysql://db:3306/auth?useSSL=false"))
                .isEqualTo("jdbc:mariadb://db:3306/auth?useSSL=false");
        assertThat(AuthDatabase.addressOf("jdbc:postgresql://db/auth")).isEqualTo("jdbc:postgresql://db/auth");
    }

    @Test
    @DisplayName("an address no carried driver speaks is refused with the ones this plugin speaks")
    void anUnknownAddressIsRefused() {
        assertThatThrownBy(() -> AuthDatabase.driverOf("jdbc:oracle:thin:@db:1521:auth"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage.jdbc-url")
                .hasMessageContaining("postgresql");
    }

    @Test
    @DisplayName("a fresh H2 database takes every migration")
    void h2TakesEveryMigration(@TempDir Path folder) {
        PluginSettings.Storage storage = new PluginSettings.Storage(
                "jdbc:h2:" + folder.resolve("accounts").toAbsolutePath(), "sa", "", 2);

        try (Database database = AuthDatabase.open(storage, "uxmauth-h2-test")) {
            assertThat(database.ping()).isTrue();
            assertThat(new SqlAccountRepository(database).findByName(new PlayerName("Steve")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("the SQLite file opens and takes every migration through the same door")
    void sqliteTakesEveryMigration(@TempDir Path folder) {
        PluginSettings.Storage storage =
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("auth.db"), "", "", 1);

        try (Database database = AuthDatabase.open(storage, "uxmauth-sqlite-test")) {
            assertThat(database.ping()).isTrue();
        }
    }
}
