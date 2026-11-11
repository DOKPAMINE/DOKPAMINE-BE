package com.dokpamine.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.QuoteDto;
import com.dokpamine.domain.record.dto.SessionDto;
import com.dokpamine.domain.record.entity.BookStatus;
import com.dokpamine.domain.record.entity.Quote;
import com.dokpamine.domain.record.entity.ReadingSession;
import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.domain.record.entity.UserBook;
import com.dokpamine.domain.record.repository.RecordInsertRepository;
import com.dokpamine.domain.record.service.RecordService;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.url=${dokpamine.test.postgresql.url:"
        + "jdbc:h2:mem:record-insert-race;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}")
class RecordInsertRaceApiTest extends ApiTestSupport {
    @MockitoSpyBean private RecordInsertRepository inserts;
    @MockitoSpyBean private RecordService recordService;
    @Autowired private PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("NFR-04 NFR-05 쪽수 축소와 세션 저장이 경합해도 스냅샷 페이지 범위를 지킨다")
    void serializesPageShrinkAndSessionWrite(boolean bookFirst) throws Exception {
        User owner = user();
        UserBook book = book(owner);
        var shrink = json.valueToTree(new BookDto(book)).asObject().put("totalPages", 100);
        var session = new SessionDto(UUID.randomUUID(), book.getId(), MONDAY,
                MONDAY.plusSeconds(600), 600, 30, 200, SessionSource.TIMER, false, MONDAY);
        String bookPath = "/api/books/" + book.getId();
        String sessionPath = "/api/sessions/" + session.id();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var accepted = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                books.findByIdForUpdate(book.getId()).orElseThrow();
                firstLocked.countDown();
                try {
                    assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
                    return putRecord(bookFirst ? bookPath : sessionPath,
                            owner, bookFirst ? shrink : session, 200);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
            try {
                assertTrue(firstLocked.await(10, TimeUnit.SECONDS));
                var rejected = executor.submit(() -> {
                    secondAttempted.countDown();
                    return putRecord(bookFirst ? sessionPath : bookPath,
                            owner, bookFirst ? session : shrink, 400);
                });
                assertTrue(secondAttempted.await(10, TimeUnit.SECONDS));
                releaseFirst.countDown();
                accepted.get(10, TimeUnit.SECONDS);
                assertEquals("BAD_REQUEST", rejected.get(10, TimeUnit.SECONDS).path("code").asString());
            } finally {
                releaseFirst.countDown();
            }
        }
        JsonNode snapshot = snapshot(owner);
        assertEquals(bookFirst ? 100 : 240, snapshot.path("books").path(0).path("totalPages").asInt());
        assertEquals(bookFirst ? 0 : 1, snapshot.path("sessions").size());
        if (!bookFirst) {
            putRecord(sessionPath, owner, session, 200);
            assertEquals(200, snapshot.path("sessions").path(0).path("endPage").asInt());
        }
    }

    @ParameterizedTest
    @EnumSource(RecordType.class)
    @DisplayName("NFR-05 타인의 최초 PUT이 UUID에서 충돌하면 403으로 거절하고 먼저 저장된 행을 보존한다")
    void rejectsConcurrentWriteByAnotherOwner(RecordType type) throws Exception {
        verifyConcurrentPut(type, user(), user(), 403);
    }

    @ParameterizedTest
    @EnumSource(RecordType.class)
    @DisplayName("NFR-05 같은 사용자의 동일한 최초 PUT 두 건이 성공하고 한 행만 남는다")
    void acceptsConcurrentRetryBySameOwner(RecordType type) throws Exception {
        User owner = user();
        verifyConcurrentPut(type, owner, owner, 200);
    }

    private void verifyConcurrentPut(RecordType type, User owner, User contender, int expectedStatus)
            throws Exception {
        UUID id = UUID.randomUUID();
        Object firstBody = body(type, id, owner, "먼저 저장한 내용", 50);
        Object secondBody = expectedStatus == 200
                ? firstBody
                : body(type, id, contender, "다른 사용자의 내용", 60);
        String path = "/api/" + type.path + "/" + id;
        CountDownLatch contenderReadyToInsert = new CountDownLatch(1);
        CountDownLatch ownerCommitted = new CountDownLatch(1);
        AtomicBoolean firstInsert = new AtomicBoolean(true);
        // 두 요청이 모두 신규 행으로 판단한 뒤 소유자의 INSERT를 먼저 커밋한다.
        Answer<Object> pauseFirstInsert = invocation -> {
            if (firstInsert.getAndSet(false)) {
                contenderReadyToInsert.countDown();
                assertTrue(ownerCommitted.await(10, TimeUnit.SECONDS), "첫 저장이 커밋되지 않았습니다.");
            }
            return invocation.callRealMethod();
        };
        switch (type) {
            case BOOK -> doAnswer(pauseFirstInsert).when(inserts).insert(any(UserBook.class));
            // 같은 책의 저장은 잠금으로 직렬화되므로 서비스 진입 전 재시도를 겹친다.
            case SESSION -> doAnswer(pauseFirstInsert).when(recordService).upsertSession(any(), any());
            case QUOTE -> doAnswer(pauseFirstInsert).when(inserts).insert(any(Quote.class));
        }

        JsonNode first;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var second = executor.submit(() -> putRecord(path, contender, secondBody, expectedStatus));
            try {
                assertTrue(contenderReadyToInsert.await(10, TimeUnit.SECONDS), "경합 요청이 INSERT에 도달하지 않았습니다.");
                first = putRecord(path, owner, firstBody, 200);
            } finally {
                ownerCommitted.countDown();
            }
            JsonNode secondResponse = second.get(10, TimeUnit.SECONDS);
            if (expectedStatus == 200) assertEquals(first, secondResponse);
        }

        JsonNode saved = snapshot(owner).path(type.path);
        assertEquals(1, saved.size());
        assertEquals(first, saved.get(0));
        if (expectedStatus == 403) assertTrue(snapshot(contender).path(type.path).isEmpty());
    }

    private Object body(RecordType type, UUID id, User user, String content, int page) {
        return switch (type) {
            case BOOK -> new BookDto(id, null, content, "저자", null, 100, "소설", null,
                    BookStatus.READING, page, 0, null, false, MONDAY, MONDAY);
            case SESSION -> new SessionDto(id, book(user).getId(), MONDAY, MONDAY.plusSeconds(600),
                    600, 0, page, SessionSource.TIMER, false, MONDAY);
            case QUOTE -> new QuoteDto(id, book(user).getId(), content, page, null, false, MONDAY, MONDAY);
        };
    }

    private JsonNode putRecord(String path, User user, Object body, int expectedStatus) throws Exception {
        var response = mvc.perform(put(path)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    private JsonNode snapshot(User user) throws Exception {
        var response = mvc.perform(get("/api/sync/snapshot")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    private enum RecordType {
        BOOK("books"), SESSION("sessions"), QUOTE("quotes");

        private final String path;

        RecordType(String path) {
            this.path = path;
        }
    }
}
