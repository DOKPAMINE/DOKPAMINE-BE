package com.dokpamine.domain.catalog.util;

import static org.junit.jupiter.api.Assertions.*;

import com.dokpamine.domain.record.entity.UserBook;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.*;

class GenreMigrationTest extends ApiTestSupport {
    @Test
    @DisplayName("FR-01 V4 마이그레이션은 이전 열두 장르를 아홉 장르로 옮기고 새 장르는 그대로 둔다")
    void mergeGenres() throws Exception {
        var user = user();
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("소설", "소설/시");
        expected.put("SF", "소설/시");
        expected.put("시/에세이", "에세이");
        expected.put("인문", "인문/사회");
        expected.put("역사", "인문/사회");
        expected.put("사회/정치", "인문/사회");
        expected.put("경제/경영", "경제/경영");
        expected.put("자기계발", "자기계발");
        expected.put("과학", "과학/IT");
        expected.put("예술", "예술");
        expected.put("만화", "만화");
        expected.put("기타", "기타");
        expected.put("소설/시", "소설/시");
        expected.put("과학/IT", "과학/IT");
        Map<UUID, String> ids = new LinkedHashMap<>();
        for (String genre : expected.keySet()) {
            UserBook book = book(user);
            book.setGenre(genre);
            ids.put(book.getId(), genre);
        }
        em.flush();

        String sql =
                new ClassPathResource("db/migration/V4__merge_genres.sql")
                        .getContentAsString(StandardCharsets.UTF_8);
        em.createNativeQuery(sql.replaceAll("(?m)^--.*$", "")).executeUpdate();
        em.clear();

        for (var entry : ids.entrySet()) {
            String migrated = books.findById(entry.getKey()).orElseThrow().getGenre();
            assertEquals(expected.get(entry.getValue()), migrated, entry.getValue());
            assertTrue(Genres.ALL.contains(migrated), migrated);
        }
    }
}
