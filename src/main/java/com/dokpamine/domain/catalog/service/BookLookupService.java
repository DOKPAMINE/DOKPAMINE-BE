package com.dokpamine.domain.catalog.service;

import com.dokpamine.domain.catalog.client.CatalogFetcher;
import com.dokpamine.domain.catalog.dto.response.CatalogBook;
import com.dokpamine.domain.catalog.util.Genres;
import com.dokpamine.domain.catalog.util.Isbn;
import com.dokpamine.global.apiPayload.exception.ApiException;
import com.dokpamine.global.config.CatalogProperties;
import com.dokpamine.global.util.TextValues;

import org.slf4j.*;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.*;

@Service
public class BookLookupService {
    private static final Logger log = LoggerFactory.getLogger(BookLookupService.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(8);
    private final CatalogProperties properties;
    private final JsonMapper json;
    private final CatalogFetcher fetcher;

    public BookLookupService(
            CatalogProperties properties, JsonMapper json, CatalogFetcher fetcher) {
        this.properties = properties;
        this.json = json;
        this.fetcher = fetcher;
    }

    public CatalogBook lookup(String rawIsbn) {
        return lookup(rawIsbn, LOOKUP_TIMEOUT);
    }

    CatalogBook lookup(String rawIsbn, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String isbn = Isbn.normalize(rawIsbn);
        if (isbn == null) throw ApiException.badRequest("올바른 ISBN이 아니에요.");
        if (TextValues.isBlank(properties.yes24ApiKey())) return lookupMetadata(isbn, deadline);

        // 기존 서지 조회의 조기 종료와 관계없이 같은 제한 시간 안에서 표지·분야를 조회한다.
        FutureTask<CatalogBook> images = new FutureTask<>(() -> yes24(isbn));
        Thread.startVirtualThread(images);
        try {
            CatalogBook metadata = lookupMetadata(isbn, deadline);
            if (Thread.currentThread().isInterrupted()) return metadata;
            try {
                return merge(metadata, await("yes24", images, deadline));
            } catch (TimeoutException e) {
                return metadata;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return metadata;
            }
        } finally {
            images.cancel(true);
        }
    }

    private CatalogBook lookupMetadata(String isbn, long deadline) {
        Map<String, Supplier<CatalogBook>> sources = new LinkedHashMap<>();
        if (!TextValues.isBlank(properties.nlkCertKey()))
            sources.put("nlk", () -> nlk(isbn));
        // FR-01: 국립중앙도서관은 KDC를 거의 주지 않아 도서관이 분류한 정보나루 KDC로 장르를 보완한다.
        if (!TextValues.isBlank(properties.data4libraryAuthKey()))
            sources.put("data4library", () -> data4library(isbn));
        if (!TextValues.isBlank(properties.kakaoRestApiKey()))
            sources.put("kakao", () -> kakao(isbn));
        if (!TextValues.isBlank(properties.googleBooksKey()))
            sources.put("google", () -> google(isbn));
        sources.put("openlibrary", () -> openLibrary(isbn));

        CatalogBook merged = null;
        for (var source : sources.entrySet()) {
            if (deadline - System.nanoTime() <= 0) break;
            FutureTask<CatalogBook> task = new FutureTask<>(source.getValue()::get);
            // 연결과 본문 읽기를 포함해 남은 시간만 기다린다.
            Thread.startVirtualThread(task);
            try {
                merged = merge(merged, await(source.getKey(), task, deadline));
                if (isComplete(merged)) break;
            } catch (TimeoutException e) {
                task.cancel(true);
                break;
            } catch (InterruptedException e) {
                task.cancel(true);
                Thread.currentThread().interrupt();
                break;
            }
        }
        return merged;
    }

    private static CatalogBook await(String source, FutureTask<CatalogBook> task, long deadline)
            throws InterruptedException, TimeoutException {
        try {
            return task.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) throw error;
            // FR-01: 예외 메시지에는 키를 포함한 URL이 있을 수 있으므로 예외 종류만 남긴다.
            log.warn("{} 도서 조회 실패: {}", source, cause.getClass().getSimpleName());
            return null;
        }
    }

    private static CatalogBook merge(CatalogBook preferred, CatalogBook fallback) {
        if (preferred == null) return fallback;
        if (fallback == null) return preferred;
        boolean yes24 = fallback.yes24ProductUrl() != null;
        return new CatalogBook(
                preferred.isbn(),
                first(preferred.title(), fallback.title()),
                first(preferred.author(), fallback.author()),
                first(preferred.publisher(), fallback.publisher()),
                first(preferred.totalPages(), fallback.totalPages()),
                yes24
                        ? yes24Genre(fallback.genre(), preferred.genre())
                        : first(preferred.genre(), fallback.genre()),
                yes24
                        ? first(fallback.coverUrl(), preferred.coverUrl())
                        : first(preferred.coverUrl(), fallback.coverUrl()),
                first(preferred.sideCoverUrl(), fallback.sideCoverUrl()),
                first(preferred.yes24ProductUrl(), fallback.yes24ProductUrl()),
                preferred.source());
    }

    /**
     * FR-01: YES24 분야는 자기계발·경제·IT처럼 서점 분류에 가까워 기본으로 우선하고 KDC 등은 보조로 쓴다. YES24가
     * 기타(수험서·여행 등)이면 정보가 없으므로 기존 장르를 유지한다.
     */
    private static String yes24Genre(String yes24, String other) {
        if (yes24 == null || other == null) return first(yes24, other);
        return yes24.equals(Genres.OTHER) ? other : yes24;
    }

    private static boolean isComplete(CatalogBook book) {
        return book != null
                && book.title() != null
                && book.author() != null
                && book.publisher() != null
                && book.totalPages() != null
                && book.genre() != null
                && book.coverUrl() != null;
    }

    private CatalogBook nlk(String isbn) {
        String body = fetcher.fetch("nlk", isbn);
        if (body == null) return null;
        for (JsonNode item : json.readTree(body).path("docs")) {
            if (!isbn.equals(Isbn.normalize(text(item, "EA_ISBN")))) continue;
            Integer pages = null;
            String pageText = text(item, "PAGE");
            if (pageText != null) {
                Matcher matches = Pattern.compile("-?[0-9]+").matcher(pageText);
                String last = null;
                while (matches.find()) last = matches.group();
                try {
                    if (last != null) pages = positive(Integer.parseInt(last));
                } catch (NumberFormatException ignored) {
                }
            }
            return new CatalogBook(
                    isbn,
                    text(item, "TITLE"),
                    firstAuthor(text(item, "AUTHOR")),
                    text(item, "PUBLISHER"),
                    pages,
                    Genres.fromKdc(text(item, "KDC")),
                    https(text(item, "TITLE_URL")),
                    null,
                    null,
                    "nlk");
        }
        return null;
    }

    private CatalogBook data4library(String isbn) {
        String body = fetcher.fetch("data4library", isbn);
        if (body == null) return null;
        // 인증 오류나 미등록 ISBN은 errCode와 빈 detail로 오므로 다음 조회처로 넘어간다.
        for (JsonNode detail : json.readTree(body).path("response").path("detail")) {
            JsonNode item = detail.path("book");
            if (!isbn.equals(Isbn.normalize(text(item, "isbn13")))) continue;
            return new CatalogBook(
                    isbn,
                    TextValues.trimToNull(text(item, "bookname")),
                    firstAuthor(text(item, "authors")),
                    text(item, "publisher"),
                    null,
                    Genres.fromKdc(text(item, "class_no")),
                    https(text(item, "bookImageURL")),
                    null,
                    null,
                    "data4library");
        }
        return null;
    }

    /** "지은이: 홍길동, 김철수 ; 옮긴이: 번역자"에서 첫 지은이만 남긴다. */
    private static String firstAuthor(String raw) {
        if (raw == null) return null;
        String author = raw.split(";", 2)[0];
        return TextValues.trimToNull(author.substring(author.indexOf(':') + 1).split(",", 2)[0]);
    }

    private CatalogBook kakao(String isbn) {
        String body = fetcher.fetch("kakao", isbn);
        if (body == null) return null;
        for (JsonNode item : json.readTree(body).path("documents")) {
            String raw = text(item, "isbn");
            if (raw == null
                    || Arrays.stream(raw.split("\\s+"))
                            .noneMatch(value -> isbn.equals(Isbn.normalize(value)))) continue;
            return new CatalogBook(
                    isbn,
                    text(item, "title"),
                    nonBlank(string(item.path("authors").path(0))),
                    text(item, "publisher"),
                    null,
                    null,
                    https(text(item, "thumbnail")),
                    null,
                    null,
                    "kakao");
        }
        return null;
    }

    private CatalogBook google(String isbn) {
        String body = fetcher.fetch("google", isbn);
        if (body == null) return null;
        JsonNode info = json.readTree(body).path("items").path(0).path("volumeInfo");
        if (info.isMissingNode()) return null;
        String title = text(info, "title");
        String subtitle = text(info, "subtitle");
        title = title == null ? subtitle : subtitle == null ? title : title + ": " + subtitle;
        return new CatalogBook(
                isbn,
                title,
                string(info.path("authors").path(0)),
                text(info, "publisher"),
                positive(info.path("pageCount").asInt(0)),
                Genres.fromCategory(string(info.path("categories").path(0))),
                https(text(info.path("imageLinks"), "thumbnail")),
                null,
                null,
                "google");
    }

    private CatalogBook openLibrary(String isbn) {
        String body = fetcher.fetch("openlibrary", isbn);
        if (body == null) return null;
        JsonNode book = json.readTree(body).path("ISBN:" + isbn);
        if (book.isMissingNode()) return null;
        String genre = null;
        for (JsonNode subject : book.path("subjects")) {
            String candidate = Genres.fromCategory(text(subject, "name"));
            if (candidate != null && !candidate.equals(Genres.OTHER)) {
                genre = candidate;
                break;
            }
        }
        return new CatalogBook(
                isbn,
                text(book, "title"),
                text(book.path("authors").path(0), "name"),
                text(book.path("publishers").path(0), "name"),
                positive(book.path("number_of_pages").asInt(0)),
                genre,
                https(text(book.path("cover"), "medium")),
                null,
                null,
                "openlibrary");
    }

    private CatalogBook yes24(String isbn) {
        String body = fetcher.fetch("yes24", isbn);
        if (body == null) return null;
        JsonNode response = json.readTree(body);
        if (!response.path("success").asBoolean(false)) return null;
        for (JsonNode item : response.path("data").path("items")) {
            if (!isbn.equals(Isbn.normalize(text(item, "isbn13")))) continue;
            // 표지를 표시할 때 출처로 연결할 수 있는 상품 정보만 사용한다.
            String productUrl = httpsUrl(text(item, "link"));
            if (productUrl == null) continue;
            return new CatalogBook(
                    isbn,
                    text(item, "title"),
                    text(item, "author"),
                    text(item, "publisher"),
                    positive(item.path("pages").asInt(0)),
                    Genres.fromYes24(text(item, "goodsSortNm")),
                    httpsUrl(text(item, "cover")),
                    httpsUrl(text(item, "sideCover")),
                    productUrl,
                    "yes24");
        }
        return null;
    }

    private static String httpsUrl(String value) {
        if (value == null || value.length() > 500) return null;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> T first(T a, T b) {
        return a != null ? a : b;
    }

    private static Integer positive(int value) {
        return value > 0 ? value : null;
    }

    private static String string(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private static String nonBlank(String value) {
        return TextValues.isBlank(value) ? null : value;
    }

    private static String text(JsonNode node, String field) {
        return nonBlank(string(node.path(field)));
    }

    private static String https(String value) {
        return value == null ? null : value.replace("http://", "https://");
    }
}
