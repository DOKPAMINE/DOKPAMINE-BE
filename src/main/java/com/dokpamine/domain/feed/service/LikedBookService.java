package com.dokpamine.domain.feed.service;

import com.dokpamine.domain.feed.dto.response.LikedBookGroup;
import com.dokpamine.domain.feed.dto.response.LikedBookPage;
import com.dokpamine.domain.feed.dto.response.LikedPerson;
import com.dokpamine.domain.feed.repository.FeedLikeReadRepository;
import com.dokpamine.domain.feed.repository.LikedReviewRow;
import com.dokpamine.domain.feed.util.BookKeys;
import com.dokpamine.domain.record.entity.Visibility;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;
import com.dokpamine.global.apiPayload.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class LikedBookService {
    public static final int MAX_SIZE = 50;
    private static final Comparator<LikedReviewRow> ROW_LATEST_FIRST =
            Comparator.comparing(LikedReviewRow::getLikedAt, Comparator.reverseOrder())
                    .thenComparing(row -> row.getReviewId().toString());
    private static final Comparator<Book> BOOK_LATEST_FIRST =
            Comparator.comparing(Book::lastLikedAt, Comparator.reverseOrder())
                    .thenComparing(Book::key);

    private final FeedLikeReadRepository likes;
    private final UserRepository users;

    public LikedBookService(FeedLikeReadRepository likes, UserRepository users) {
        this.likes = likes;
        this.users = users;
    }

    /**
     * FR-09 내가 좋아요한 공개 감상을 책 묶음 단위로 나눠 준다. 같은 책이 두 페이지에 나뉘지 않도록 묶은 뒤에 자른다. 한 사람이 누른 좋아요는
     * 많지 않으므로 전부 읽어 묶는다.
     */
    public LikedBookPage list(UUID me, int size, String cursor) {
        if (size < 1 || size > MAX_SIZE)
            throw ApiException.badRequest("한 번에 1개부터 " + MAX_SIZE + "개까지 불러올 수 있어요.");
        Cursor after = Cursor.decode(cursor);
        List<Book> books =
                likes.findVisibleLikedReviews(me, Visibility.PUBLIC).stream()
                        .collect(
                                Collectors.groupingBy(
                                        row ->
                                                BookKeys.of(
                                                        row.getIsbn(),
                                                        row.getTitle(),
                                                        row.getAuthor())))
                        .entrySet()
                        .stream()
                        .map(entry -> Book.of(entry.getKey(), entry.getValue()))
                        .filter(book -> after == null || after.isBefore(book))
                        .sorted(BOOK_LATEST_FIRST)
                        .toList();
        List<Book> page = books.subList(0, Math.min(size, books.size()));
        String next = books.size() > size ? Cursor.encode(page.getLast()) : null;
        Map<UUID, String> nicknames =
                users
                        .findAllById(
                                page.stream()
                                        .flatMap(book -> book.rows().stream())
                                        .map(LikedReviewRow::getAuthorId)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        return new LikedBookPage(page.stream().map(book -> book.toGroup(nicknames, me)).toList(), next);
    }

    /** 묶음 안의 좋아요는 최근 순으로 정렬해 둔다. 대표 제목·표지는 가장 최근에 좋아요한 감상의 책에서 가져온다. */
    private record Book(String key, List<LikedReviewRow> rows) {
        static Book of(String key, List<LikedReviewRow> rows) {
            return new Book(key, rows.stream().sorted(ROW_LATEST_FIRST).toList());
        }

        Instant lastLikedAt() {
            return rows.getFirst().getLikedAt();
        }

        LikedBookGroup toGroup(Map<UUID, String> nicknames, UUID me) {
            LikedReviewRow latest = rows.getFirst();
            String coverUrl =
                    rows.stream()
                            .map(LikedReviewRow::getCoverUrl)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
            // rows가 최근 순이므로 사람의 책도 처음 나온 순서가 마지막 좋아요 순서다.
            Map<UUID, List<LikedReviewRow>> byUserBook =
                    rows.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            LikedReviewRow::getUserBookId,
                                            LinkedHashMap::new,
                                            Collectors.toList()));
            List<LikedPerson> people =
                    byUserBook.values().stream()
                            .map(
                                    liked -> {
                                        LikedReviewRow first = liked.getFirst();
                                        return new LikedPerson(
                                                first.getAuthorId(),
                                                nicknames.get(first.getAuthorId()),
                                                first.getUserBookId(),
                                                liked.size(),
                                                first.getLikedAt(),
                                                first.getAuthorId().equals(me));
                                    })
                            .toList();
            return new LikedBookGroup(
                    key,
                    BookKeys.isbn(key),
                    latest.getTitle(),
                    latest.getAuthor(),
                    coverUrl,
                    latest.getLikedAt(),
                    people);
        }
    }

    /** 앱에는 내용을 알 수 없는 문자열로 주고, 마지막으로 받은 묶음의 정렬 위치만 담는다. */
    private record Cursor(Instant lastLikedAt, String key) {
        static String encode(Book book) {
            String raw = book.lastLikedAt() + " " + book.key();
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                String raw =
                        new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split(" ", 2);
                if (parts.length != 2 || parts[1].isEmpty()) throw invalid();
                return new Cursor(Instant.parse(parts[0]), parts[1]);
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw invalid();
            }
        }

        /** BOOK_LATEST_FIRST 순서에서 이 커서 위치보다 뒤에 오는 묶음인지 본다. */
        boolean isBefore(Book book) {
            int byTime = book.lastLikedAt().compareTo(lastLikedAt);
            return byTime < 0 || (byTime == 0 && book.key().compareTo(key) > 0);
        }

        private static ApiException invalid() {
            return ApiException.badRequest("다음 페이지 정보가 올바르지 않아요.");
        }
    }
}
