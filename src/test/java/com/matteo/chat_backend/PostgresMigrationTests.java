package com.matteo.chat_backend;

import com.matteo.chat_backend.entity.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL on a random local port. Never reads DB_* or Spring datasource configuration. */
class PostgresMigrationTests {
    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder()
                .setServerConfig("listen_addresses", "127.0.0.1")
                .start();
        var jdbc = new JdbcTemplate(postgres.getPostgresDatabase());
        jdbc.execute("CREATE ROLE anon NOLOGIN");
        jdbc.execute("CREATE ROLE authenticated NOLOGIN");
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    private DataSource newDatabase() {
        String name = "migration_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(postgres.getPostgresDatabase()).execute("CREATE DATABASE " + name);
        return postgres.getDatabase("postgres", name);
    }

    private Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
                .baselineOnMigrate(false).baselineVersion("2").cleanDisabled(true).load();
    }

    @Test
    void emptyDatabaseUsesSnapshotAndMatchesAllJpaEntities() {
        var dataSource = newDatabase();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DEFAULT PRIVILEGES GRANT ALL ON TABLES TO anon, authenticated");
        var flyway = flyway(dataSource);
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals("B14__complete_chat_schema.sql", flyway.info().current().getScript());
        validateEntities(dataSource);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertApiRolesRestricted(jdbc);

        try (var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "validate").build();
             var factory = metadata(registry).buildMetadata().buildSessionFactory();
             var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            var user = new User();
            user.setUsername("postgres-smoke");
            user.setEmail("postgres-smoke@example.invalid");
            user.setPasswordHash("test-only-hash");
            session.persist(user);
            var server = new Server();
            server.setName("Migration smoke");
            server.setOwner(user);
            session.persist(server);
            var channel = new Channel();
            channel.setName("general");
            channel.setServer(server);
            session.persist(channel);
            var message = new Message();
            message.setSender(user);
            message.setChannel(channel);
            message.setContent("test-message-".repeat(100));
            session.persist(message);
            transaction.commit();
            session.clear();
            assertEquals(message.getContent(), session.find(Message.class, message.getId()).getContent());
        }
    }

    @Test
    void legacyHistoryUpgradesThroughV14WithoutLosingMessages() throws Exception {
        var dataSource = newDatabase();
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/common/V1__create_app_users_table.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy-v2.sql"));
        }
        var jdbc = new JdbcTemplate(dataSource);
        var userId = UUID.randomUUID();
        var serverId = UUID.randomUUID();
        var messageId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash) VALUES (?, 'legacy', 'legacy@example.invalid', 'hash')", userId);
        jdbc.update("INSERT INTO servers(id, name, owner_id) VALUES (?, 'legacy', ?)", serverId, userId);
        jdbc.update("INSERT INTO server_members(id, server_id, user_id, role) VALUES (?, ?, ?, 'member')", UUID.randomUUID(), serverId, userId);
        jdbc.update("INSERT INTO messages(id, sender_id, content) VALUES (?, ?, 'preserved encrypted payload')", messageId, userId);
        var oldVersion = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
                .baselineOnMigrate(false).baselineVersion("2").target("13")
                .cleanDisabled(true).load();
        oldVersion.baseline(); // Explicit adoption of this known fixture, never automatic adoption.
        oldVersion.migrate();
        assertEquals("13", oldVersion.info().current().getVersion().getVersion());
        var flyway = flyway(dataSource);
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals("V14__add_soft_delete_deleted_at.sql", flyway.info().current().getScript());
        assertEquals("preserved encrypted payload", jdbc.queryForObject("SELECT content FROM messages WHERE id = ?", String.class, messageId));
        assertEquals("owner", jdbc.queryForObject("SELECT role FROM server_members", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT token_version FROM users", Integer.class));
        assertNull(jdbc.queryForObject("SELECT deleted_at FROM messages", Object.class));
        validateEntities(dataSource);
        assertApiRolesRestricted(jdbc);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        var fresh = newDatabase();
        flyway(fresh).migrate();
        String columns = "SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable "
                + "|| ':' || COALESCE(character_maximum_length::text, '') "
                + "FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY table_name, column_name";
        assertEquals(new JdbcTemplate(fresh).queryForList(columns, String.class),
                jdbc.queryForList(columns, String.class), "Fresh and upgraded mapped column definitions must match");
    }

    @Test
    void unknownNonEmptySchemaIsNotAutomaticallyAdopted() {
        var dataSource = newDatabase();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE unrelated_data (id INTEGER PRIMARY KEY)");
        jdbc.execute("INSERT INTO unrelated_data VALUES (42)");
        assertThrows(FlywayException.class, () -> flyway(dataSource).migrate());
        assertEquals(42, jdbc.queryForObject("SELECT id FROM unrelated_data", Integer.class));
    }

    private static MetadataSources metadata(org.hibernate.service.ServiceRegistry registry) {
        var metadata = new MetadataSources(registry);
        for (var type : List.of(User.class, Server.class, Channel.class, ServerMember.class,
                DirectMessage.class, Message.class, Friendship.class, ServerInvitation.class, AuthSession.class)) {
            metadata.addAnnotatedClass(type);
        }
        return metadata;
    }

    private static void validateEntities(DataSource dataSource) {
        try (var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "validate").build();
             var ignored = metadata(registry).buildMetadata().buildSessionFactory()) {
            // Hibernate fails construction if any mapped table/column/type is missing or incompatible.
        }
    }

    private static void assertApiRolesRestricted(JdbcTemplate jdbc) {
        for (var table : List.of("app_users", "users", "servers", "channels", "server_members",
                "direct_messages", "messages", "friendships", "server_invitations", "auth_sessions")) {
            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    "SELECT relrowsecurity FROM pg_class WHERE oid = CAST(? AS regclass)", Boolean.class, table));
            for (var role : List.of("anon", "authenticated")) {
                assertEquals(Boolean.FALSE, jdbc.queryForObject(
                        "SELECT has_table_privilege(?, ?, 'SELECT,INSERT,UPDATE,DELETE')", Boolean.class, role, table));
            }
        }
    }
}
