package com.dokpamine.domain.user.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

class UsernameMigrationTest {
    @Test
    @DisplayName("FR-10 V6 마이그레이션 후에도 기존 로그인 값과 사용자 UUID가 유지된다")
    void preservesExistingAccount() throws Exception {
        String url = "jdbc:h2:mem:username-migration;MODE=PostgreSQL";
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
            try (var insert = connection.prepareStatement("""
                    insert into users (id, email, password_hash, nickname, created_at)
                    values (?, 'reader@test.com', 'existing-hash', '독자', current_timestamp)
                    """)) {
                insert.setObject(1, id);
                insert.executeUpdate();
            }

            Flyway.configure().dataSource(url, "sa", "").load().migrate();

            try (var query = connection.createStatement();
                    var rows = query.executeQuery("select id, username, password_hash from users")) {
                assertTrue(rows.next());
                assertEquals(id, rows.getObject("id", UUID.class));
                assertEquals("reader@test.com", rows.getString("username"));
                assertEquals("existing-hash", rows.getString("password_hash"));
                assertFalse(rows.next());
            }
        }
    }
}
