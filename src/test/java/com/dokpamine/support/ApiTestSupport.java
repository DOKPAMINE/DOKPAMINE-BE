package com.dokpamine.support;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dokpamine.domain.catalog.client.CatalogFetcher;
import com.dokpamine.domain.catalog.service.BookLookupService;
import com.dokpamine.domain.record.entity.*;
import com.dokpamine.domain.record.repository.*;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@SpringBootTest(
        properties = {
            "dokpamine.catalog.nlk-cert-key=test",
            "dokpamine.catalog.kakao-rest-api-key=test",
            "dokpamine.catalog.google-books-key=test",
            "dokpamine.catalog.yes24-api-key=test",
            "dokpamine.catalog.data4library-auth-key=test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected UserBookRepository books;
    @Autowired protected ReadingSessionRepository sessions;
    @Autowired protected ReviewRepository reviews;
    @Autowired protected EntityManager em;
    @MockitoBean protected CatalogFetcher fetcher;
    @Autowired protected BookLookupService lookups;
    protected static final Instant MONDAY = Instant.parse("2026-10-25T15:00:00Z");

    @BeforeEach
    void clearExternalResponses() {
        reset(fetcher);
        // 테스트마다 외부 응답을 바꾸므로 이전 테스트의 ISBN 조회 결과를 쓰지 않는다.
        lookups.clearCache();
    }

    protected User user() {
        // FR-10: 닉네임은 고유해야 하므로 한 테스트에서 여러 명을 만들어도 겹치지 않게 한다.
        return user("독자" + UUID.randomUUID().toString().substring(0, 8), false);
    }

    protected User user(String nickname, boolean optedIn) {
        return users.save(
                new User(
                        UUID.randomUUID(),
                        UUID.randomUUID() + "@test.com",
                        "unused",
                        nickname,
                        optedIn,
                        MONDAY));
    }

    protected UserBook book(User user) {
        return book(user, "데미안", "헤르만 헤세", null);
    }

    protected UserBook book(User user, String title, String author, String isbn) {
        return books.save(
                new UserBook(
                        UUID.randomUUID(),
                        user.getId(),
                        isbn,
                        title,
                        author,
                        null,
                        240,
                        "소설",
                        null,
                        null,
                        null,
                        BookStatus.READING,
                        0,
                        0,
                        null,
                        false,
                        MONDAY,
                        MONDAY));
    }

    protected ReadingSession session(
            User user, int seconds, Instant start, SessionSource source, boolean deleted) {
        UserBook book = book(user);
        return sessions.save(
                new ReadingSession(
                        UUID.randomUUID(),
                        user.getId(),
                        book.getId(),
                        start,
                        start.plusSeconds(seconds),
                        seconds,
                        0,
                        null,
                        source,
                        deleted,
                        MONDAY));
    }

    protected Review review(User user, UserBook book, Visibility visibility, Instant published) {
        Review review =
                new Review(
                        UUID.randomUUID(),
                        user.getId(),
                        book.getId(),
                        "감상",
                        "본문",
                        visibility,
                        published,
                        false,
                        MONDAY,
                        MONDAY);
        // 독후감이 있는 감상이므로 라운지 박스 정렬 시각도 공개 시각과 같게 둔다.
        review.setBodyWrittenAt(published != null ? published : MONDAY);
        return reviews.save(review);
    }

    protected JsonNode request(
            MockHttpServletRequestBuilder request, User user, Object body, int expected)
            throws Exception {
        if (user != null) request.with(jwt().jwt(token -> token.subject(user.getId().toString())));
        if (body != null)
            request.contentType(APPLICATION_JSON).content(json.writeValueAsString(body));
        String value =
                mvc.perform(request)
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        if (expected < 300) {
            em.flush();
            em.clear();
        }
        return value.isBlank() ? json.nullNode() : json.readTree(value);
    }

    protected static List<JsonNode> list(JsonNode array) {
        List<JsonNode> values = new ArrayList<>();
        array.forEach(values::add);
        return values;
    }
}
