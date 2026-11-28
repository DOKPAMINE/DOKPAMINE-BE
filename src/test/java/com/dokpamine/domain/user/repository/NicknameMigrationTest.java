package com.dokpamine.domain.user.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class NicknameMigrationTest {
    private static final String URL =
            "jdbc:h2:mem:nickname-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    @DisplayName("FR-10 V8 마이그레이션은 중복 닉네임 중 가장 먼저 가입한 사용자만 남기고 나머지를 15자 이내 고유 닉네임으로 바꾼다")
    void deduplicatesNicknames() throws Exception {
        flyway("7").migrate();
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        UUID oldest = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        UUID latest = UUID.randomUUID();
        UUID longLater = UUID.randomUUID();
        UUID unique = UUID.randomUUID();
        String longNickname = "열다섯자를훌쩍넘기는아주긴예전닉네임입니다";
        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            insert(connection, later, "독자", base.plusSeconds(10));
            insert(connection, oldest, "독자", base);
            insert(connection, latest, "독자", base.plusSeconds(20));
            insert(connection, UUID.randomUUID(), longNickname, base);
            insert(connection, longLater, longNickname, base.plusSeconds(10));
            insert(connection, unique, "혼자", base);
        }

        flyway(null).migrate();

        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            Map<UUID, String> nicknames = nicknames(connection);
            assertEquals("독자", nicknames.get(oldest));
            assertEquals("혼자", nicknames.get(unique));
            assertEquals("독자#" + compact(later), nicknames.get(later));
            assertEquals("독자#" + compact(latest), nicknames.get(latest));
            assertEquals(longNickname.substring(0, 6) + "#" + compact(longLater), nicknames.get(longLater));
            assertEquals(nicknames.size(), nicknames.values().stream().distinct().count());
            nicknames.forEach((id, nickname) -> {
                if (!nickname.equals(longNickname)) assertTrue(nickname.length() <= 15, nickname);
            });
            assertThrows(SQLException.class,
                    () -> insert(connection, UUID.randomUUID(), "독자", base.plusSeconds(30)));
        }
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private static void insert(Connection connection, UUID id, String nickname, Instant createdAt)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "insert into users (id, username, password_hash, nickname, ranking_opt_in, created_at)"
                        + " values (?, ?, 'unused', ?, false, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, id.toString());
            statement.setString(3, nickname);
            statement.setTimestamp(4, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
    }

    private static Map<UUID, String> nicknames(Connection connection) throws SQLException {
        Map<UUID, String> values = new HashMap<>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("select id, nickname from users")) {
            while (rows.next()) values.put(rows.getObject("id", UUID.class), rows.getString("nickname"));
        }
        return values;
    }

    private static String compact(UUID id) {
        return id.toString().replace("-", "").substring(0, 8);
    }
}
