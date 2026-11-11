package com.dokpamine.domain.record.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dokpamine.domain.record.dto.BookDto;
import com.dokpamine.domain.record.dto.SessionDto;
import com.dokpamine.domain.record.entity.SessionSource;
import com.dokpamine.support.ApiTestSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

class RecordPageRangeApiTest extends ApiTestSupport {
    @ParameterizedTest
    @CsvSource({"30,200", "200,null", "200,50"})
    @DisplayName("NFR-04 NFR-05 쪽수 축소 거부와 경계값 축소 후 세션 재전송·스냅샷은 기록을 보존한다")
    void shrinkingPreservesReplayAndSnapshot(int startPage, String endPage) throws Exception {
        var owner = user();
        var book = book(owner);
        var originalBook = new BookDto(book);
        String bookPath = "/api/books/" + book.getId();
        var session = new SessionDto(UUID.randomUUID(), book.getId(), MONDAY,
                MONDAY.plusSeconds(600), 600, startPage,
                endPage.equals("null") ? null : Integer.valueOf(endPage),
                SessionSource.TIMER, false, MONDAY);
        String sessionPath = "/api/sessions/" + session.id();
        var saved = request(put(sessionPath), owner, session, 200);
        var shrink = json.valueToTree(originalBook).asObject()
                .put("totalPages", 100).put("currentPage", 100).put("title", "변경 시도");
        request(put(bookPath), user(), shrink, 403);
        request(put(bookPath), owner, shrink, 400);
        var before = request(get("/api/sync/snapshot"), owner, null, 200);
        assertEquals(json.valueToTree(originalBook), before.path("books").path(0));
        assertEquals(saved, before.path("sessions").path(0));

        shrink.put("totalPages", 200);
        var accepted = request(put(bookPath), owner, shrink, 200);
        for (int i = 0; i < 2; i++) {
            assertEquals(saved, request(put(sessionPath), owner, session, 200));
        }
        var snapshot = request(get("/api/sync/snapshot"), owner, null, 200);
        assertEquals(1, snapshot.path("books").size());
        assertEquals(1, snapshot.path("sessions").size());
        assertEquals(accepted, snapshot.path("books").path(0));
        assertEquals(saved, snapshot.path("sessions").path(0));
        // 복원 시 내려받은 DTO를 그대로 다시 올려도 같은 기록으로 남는다.
        request(put(bookPath), owner, snapshot.path("books").path(0), 200);
        assertEquals(saved, request(put(sessionPath), owner, snapshot.path("sessions").path(0), 200));
    }

    @Test
    @DisplayName("NFR-04 NFR-05 삭제된 세션은 쪽수 축소를 막지 않으며 범위 밖 복원은 거부한다")
    void deletedSessionDoesNotBlockShrink() throws Exception {
        var owner = user();
        var book = book(owner);
        var session = new SessionDto(UUID.randomUUID(), book.getId(), MONDAY,
                MONDAY.plusSeconds(600), 600, 150, 200, SessionSource.TIMER, false, MONDAY);
        String path = "/api/sessions/" + session.id();
        request(put(path), owner, session, 200);
        var deletion = json.valueToTree(session).asObject().put("deleted", true);
        request(put(path), owner, deletion, 200);
        var shrink = json.valueToTree(new BookDto(book)).asObject().put("totalPages", 100);
        request(put("/api/books/" + book.getId()), owner, shrink, 200);
        for (int i = 0; i < 2; i++) request(put(path), owner, deletion, 200);
        request(put(path), owner, session, 400);
        var newSession = json.valueToTree(session).asObject()
                .put("id", UUID.randomUUID().toString()).putNull("endPage");
        request(put("/api/sessions/" + newSession.path("id").asString()), owner, newSession, 400);
        var snapshot = request(get("/api/sync/snapshot"), owner, null, 200);
        assertEquals(100, snapshot.path("books").path(0).path("totalPages").asInt());
        assertTrue(snapshot.path("sessions").isEmpty());
        var stored = sessions.findById(session.id()).orElseThrow();
        assertTrue(stored.getDeleted());
        assertEquals(150, stored.getStartPage());
        assertEquals(200, stored.getEndPage());
    }

    @Test
    @DisplayName("NFR-05 책 삭제는 세션 범위와 무관하게 허용하고 세션을 수정하지 않는다")
    void bookDeletionStillWorks() throws Exception {
        var owner = user();
        var book = book(owner);
        var session = new SessionDto(UUID.randomUUID(), book.getId(), MONDAY,
                MONDAY.plusSeconds(600), 600, 30, 200, SessionSource.TIMER, false, MONDAY);
        request(put("/api/sessions/" + session.id()), owner, session, 200);
        var deletion = json.valueToTree(new BookDto(book)).asObject()
                .put("totalPages", 100).put("deleted", true);
        request(put("/api/books/" + book.getId()), owner, deletion, 200);
        assertEquals(200, sessions.findById(session.id()).orElseThrow().getEndPage());
        var snapshot = request(get("/api/sync/snapshot"), owner, null, 200);
        assertTrue(snapshot.path("books").isEmpty());
        assertTrue(snapshot.path("sessions").isEmpty());
    }
}
