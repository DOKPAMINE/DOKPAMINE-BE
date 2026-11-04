package com.dokpamine.domain.feed.service;

import com.dokpamine.domain.catalog.util.Isbn;
import com.dokpamine.domain.feed.dto.response.BookReviewGroup;
import com.dokpamine.domain.feed.dto.response.FeedItem;
import com.dokpamine.domain.feed.repository.BookReviewSearchRepository;
import com.dokpamine.domain.feed.repository.PublicBookSummary;
import com.dokpamine.domain.feed.support.FeedItemMapper;
import com.dokpamine.domain.feed.util.NativePublishedAt;
import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.Visibility;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.util.TextValues;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BookReviewSearchService {
    public static final int TITLE_CANDIDATE_LIMIT = 500;
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Comparator<PublicBookSummary> LATEST_FIRST =
            Comparator.comparing(
                            (PublicBookSummary book) ->
                                    Objects.requireNonNullElse(
                                            NativePublishedAt.toInstant(
                                                    book.getLatestPublishedAt()),
                                            Instant.MIN),
                            Comparator.reverseOrder())
                    .thenComparing(PublicBookSummary::getLatestReviewId);
    private final BookReviewSearchRepository books;
    private final FeedItemMapper mapper;

    public BookReviewSearchService(BookReviewSearchRepository books, FeedItemMapper mapper) {
        this.books = books;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<BookReviewGroup> search(String query, UUID me) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) throw ApiException.badRequest("검색어를 입력해 주세요.");
        String pattern =
                Arrays.stream(normalized.split(" "))
                        .map(token ->
                                token.replace("!", "!!").replace("%", "!%").replace("_", "!_"))
                        .collect(Collectors.joining("%", "%", "%"));
        Set<String> matchingKeys =
                books
                        .findTitleCandidates(
                                pattern,
                                Visibility.PUBLIC,
                                PageRequest.of(0, TITLE_CANDIDATE_LIMIT))
                        .stream()
                        .filter(book -> normalize(book.getTitle()).contains(normalized))
                        .map(book -> bookKey(book.getIsbn(), book.getTitle(), book.getAuthor()))
                        .collect(Collectors.toSet());
        if (matchingKeys.isEmpty()) return List.of();
        List<String> isbns =
                matchingKeys.stream()
                        .filter(key -> key.startsWith("isbn:"))
                        .map(key -> key.substring(5))
                        .toList();
        Map<String, List<PublicBookSummary>> groups =
                books
                        .findPublicBookSummaries(
                                pattern,
                                isbns,
                                isbns.stream()
                                        .flatMap(value -> isbnSearchForms(value).stream())
                                        .toList())
                        .stream()
                        .filter(book ->
                                matchingKeys.contains(
                                        bookKey(book.getIsbn(), book.getTitle(), book.getAuthor())))
                        .collect(
                                Collectors.groupingBy(book ->
                                        bookKey(
                                                book.getIsbn(),
                                                book.getTitle(),
                                                book.getAuthor())));
        groups.values().forEach(entries -> entries.sort(LATEST_FIRST));
        List<Map.Entry<String, List<PublicBookSummary>>> selected =
                groups.entrySet().stream()
                        .sorted((a, b) ->
                                LATEST_FIRST.compare(
                                        a.getValue().getFirst(), b.getValue().getFirst()))
                        .limit(20)
                        .toList();
        Map<String, List<Review>> matches = new LinkedHashMap<>();
        for (var entry : selected)
            matches.put(
                    entry.getKey(),
                    books.findPublicReviews(
                            entry.getValue().stream()
                                    .map(book -> UUID.fromString(book.getId()))
                                    .toList(),
                            Visibility.PUBLIC,
                            PageRequest.of(0, 100)));
        List<Review> reviews = matches.values().stream().flatMap(Collection::stream).toList();
        if (reviews.isEmpty()) return List.of();
        Map<UUID, FeedItem> mapped =
                mapper.toItems(reviews, me).stream()
                        .collect(Collectors.toMap(FeedItem::id, Function.identity()));
        List<BookReviewGroup> result = new ArrayList<>();
        for (var match : matches.entrySet()) {
            List<FeedItem> items =
                    match.getValue().stream()
                            .map(review -> mapped.get(review.getId()))
                            .filter(Objects::nonNull)
                            .toList();
            if (items.isEmpty()) continue;
            FeedItem latest = items.getFirst();
            String key = match.getKey();
            result.add(
                    new BookReviewGroup(
                            key,
                            latest.bookTitle(),
                            latest.bookAuthor(),
                            key.startsWith("isbn:") ? key.substring(5) : null,
                            items));
        }
        return result;
    }

    private static String bookKey(String rawIsbn, String title, String author) {
        String trimmed = TextValues.trim(rawIsbn);
        if (trimmed != null && !trimmed.isEmpty()) {
            return "isbn:" + Objects.requireNonNullElse(Isbn.normalize(trimmed), trimmed);
        }
        String text = normalize(title) + "\n" + normalize(author);
        return "text:"
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> isbnSearchForms(String isbn) {
        if (!isbn.equals(Isbn.normalize(isbn))) return List.of();
        if (!isbn.startsWith("978")) return List.of(isbn);
        String firstNine = isbn.substring(3, 12);
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (10 - i) * (firstNine.charAt(i) - '0');
        int check = (11 - sum % 11) % 11;
        return List.of(isbn, firstNine + (check == 10 ? "X" : Integer.toString(check)));
    }

    private static String normalize(String value) {
        return TextValues.trim(WHITESPACE.matcher(value).replaceAll(" ")).toLowerCase(Locale.ROOT);
    }
}
