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
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.stubbing.Answer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:record-insert-race;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class RecordInsertRaceApiTest extends ApiTestSupport {
    @MockitoSpyBean private RecordInsertRepository inserts;

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
            case SESSION -> doAnswer(pauseFirstInsert).when(inserts).insert(any(ReadingSession.class));
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
