package com.dokpamine.domain.ranking.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.domain.record.entity.*;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.*;

import tools.jackson.databind.JsonNode;

import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

class RankingApiTest extends ApiTestSupport {
    private void session(User user, int seconds, int day) {
        session(user, seconds, MONDAY.plusSeconds(day * 86400L), SessionSource.TIMER, false);
    }

    private JsonNode ranking(User user) throws Exception {
        return request(get("/api/ranking/weekly?weekStart=2026-10-28"), user, null, 200);
    }

    @Test
    @DisplayName("FR-15 일별 상한과 동점 도달 시각으로 연속 순위를 매긴다")
    void totalsAndTie() throws Exception {
        User a = user("A", true), c = user("C", true), b = user("B", true);
        session(a, 10800, 0);
        session(a, 7200, 1);
        session(c, 6000, 0);
        session(c, 6000, MONDAY.plusSeconds(90000), SessionSource.TIMER, false);
        session(b, 10800, 0);
        session(b, 1200, 1);
        JsonNode result = ranking(b);
        assertEquals("2026-10-26", result.path("weekStart").asString());
        assertEquals(
                List.of("A", "B", "C"),
                list(result.path("top")).stream().map(n -> n.path("nickname").asString()).toList());
        assertEquals(
                List.of(18000L, 12000L, 12000L),
                list(result.path("top")).stream().map(n -> n.path("seconds").asLong()).toList());
        assertEquals(
                List.of(1, 2, 3),
                list(result.path("top")).stream().map(n -> n.path("rank").asInt()).toList());
        assertEquals(0, result.path("around").size());
    }

    @Test
    @DisplayName("FR-15 45명 중 38위 주변은 16위부터 45위이며 상위 30명과 함께 반환한다")
    void aroundAtEnd() throws Exception {
        List<User> all = new ArrayList<>();
        for (int rank = 1; rank <= 45; rank++) {
            User user = user("독자" + rank, true);
            session(user, 1000 - rank, 0);
            all.add(user);
        }
        JsonNode result = ranking(all.get(37));
        assertEquals(
                IntStream.rangeClosed(1, 30).boxed().toList(),
                list(result.path("top")).stream().map(n -> n.path("rank").asInt()).toList());
        assertEquals(
                IntStream.rangeClosed(16, 45).boxed().toList(),
                list(result.path("around")).stream().map(n -> n.path("rank").asInt()).toList());
        assertEquals(
                38,
                list(result.path("around")).stream()
                        .filter(n -> n.path("me").asBoolean())
                        .findFirst()
                        .orElseThrow()
                        .path("rank")
                        .asInt());
        assertEquals(0, ranking(all.get(29)).path("around").size());
    }

    @Test
    @DisplayName("FR-15 중간 순위는 본인 위 15명과 아래 14명이다")
    void aroundInMiddle() throws Exception {
        List<User> all = new ArrayList<>();
        for (int rank = 1; rank <= 70; rank++) {
            User user = user("독자" + rank, true);
            session(user, 1000 - rank, 0);
            all.add(user);
        }
        assertEquals(
                IntStream.rangeClosed(23, 52).boxed().toList(),
                list(ranking(all.get(37)).path("around")).stream()
                        .map(n -> n.path("rank").asInt())
                        .toList());
    }

    @Test
    @DisplayName("FR-15 MANUAL 삭제 비참여는 제외되고 참여 중단은 즉시 반영된다")
    void participation() throws Exception {
        User active = user("참여", true), hidden = user("비참여", false), manual = user("수동", true);
        session(active, 12000, 0);
        session(hidden, 10800, 0);
        session(manual, 10800, MONDAY, SessionSource.MANUAL, false);
        session(manual, 10800, MONDAY, SessionSource.TIMER, true);
        JsonNode result = ranking(hidden);
        assertFalse(result.path("participating").asBoolean());
        assertEquals(1, result.path("top").size());
        assertEquals(10800, result.path("top").path(0).path("seconds").asInt());
        request(patch("/api/me"), active, Map.of("rankingOptIn", false), 200);
        assertEquals(0, ranking(active).path("top").size());
        assertTrue(ranking(manual).path("participating").asBoolean());
    }

    @Test
    @DisplayName("FR-15 상한 뒤 세션과 0초 세션은 도달 시각을 늦추지 않는다")
    void capDoesNotDelay() throws Exception {
        User early = user("먼저", true), later = user("나중", true);
        session(early, 10800, 0);
        session(early, 60, MONDAY.plusSeconds(20000), SessionSource.TIMER, false);
        session(early, 0, 1);
        session(later, 10800, MONDAY.plusSeconds(60), SessionSource.TIMER, false);
        assertEquals("먼저", ranking(later).path("top").path(0).path("nickname").asString());
    }

    @Test
    @DisplayName("FR-15 KST 주 시작은 포함하고 다음 주 시작은 제외하며 최종 동점은 UUID 문자열로 정렬한다")
    void boundaries() throws Exception {
        User first =
                users.save(
                        new User(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                "first@test.com",
                                "unused",
                                "첫째",
                                true,
                                MONDAY));
        User second =
                users.save(
                        new User(
                                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                                "second@test.com",
                                "unused",
                                "둘째",
                                true,
                                MONDAY));
        session(second, 60, 0);
        session(first, 60, 0);
        session(first, 600, MONDAY.minusSeconds(1), SessionSource.TIMER, false);
        session(first, 600, 7);
        JsonNode result = ranking(first);
        assertEquals(
                List.of("첫째", "둘째"),
                list(result.path("top")).stream().map(n -> n.path("nickname").asString()).toList());
        assertEquals(
                List.of(60, 60),
                list(result.path("top")).stream().map(n -> n.path("seconds").asInt()).toList());
    }

    @Test
    @DisplayName("FR-15 기록 없는 참여자는 빈 랭킹을 받고 인증 없는 요청은 거절한다")
    void empty() throws Exception {
        JsonNode result = ranking(user("없음", true));
        assertEquals(0, result.path("top").size());
        request(get("/api/ranking/weekly"), null, null, 401);
    }
}
