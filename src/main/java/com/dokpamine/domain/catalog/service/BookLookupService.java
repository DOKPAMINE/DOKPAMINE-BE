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
    // FR-01: 앱의 요청 제한 시간(10초)보다 충분히 짧게 둔다.
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration YES24_SIDE_COVER_TIMEOUT = Duration.ofSeconds(1);
    // 앞 조회처가 이 시간 안에 응답하지 않으면 다음 조회처를 미리 시작한다. 평소처럼 빨리 응답하면 순서대로만 부른다.
    private static final Duration HEDGE_DELAY = Duration.ofSeconds(1);
    private static final Duration CACHE_TTL = Duration.ofDays(1);
    private static final int CACHE_SIZE = 500;
    private final CatalogProperties properties;
    private final JsonMapper json;
    private final CatalogFetcher fetcher;
    // 같은 책을 다시 스캔할 때 외부 API를 기다리지 않도록 최근 결과를 접근 순서대로 보관한다.
    private final Map<String, Cached> cache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private record Cached(CatalogBook book, long expiresAt) {}

    /** 조회 중 제한 시간이나 오류가 있었는지 기록한다. 그런 결과는 다시 조회하면 달라질 수 있어 보관하지 않는다. */
    private static final class Attempt {
        boolean degraded;
    }

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
        CatalogBook cached = cached(isbn);
        if (cached != null) return cached;
        Attempt attempt = new Attempt();
        CatalogBook book = fetch(isbn, deadline, attempt);
        // 404는 보관하지 않아 나중에 등록된 책도 다시 찾을 수 있게 한다.
        if (book != null && !attempt.degraded) store(isbn, book);
        return book;
    }

    /** 외부 조회 응답을 바꾼 테스트가 이전 결과를 받지 않도록 비운다. */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    private CatalogBook cached(String isbn) {
        synchronized (cache) {
            Cached entry = cache.get(isbn);
            if (entry == null) return null;
            if (entry.expiresAt() - System.nanoTime() > 0) return entry.book();
            cache.remove(isbn);
            return null;
        }
    }

    private void store(String isbn, CatalogBook book) {
        synchronized (cache) {
            cache.put(isbn, new Cached(book, System.nanoTime() + CACHE_TTL.toNanos()));
        }
    }

    private CatalogBook fetch(String isbn, long deadline, Attempt attempt) {
        if (TextValues.isBlank(properties.yes24ApiKey()))
            return lookupMetadata(isbn, deadline, attempt);

        // 기존 서지 조회의 조기 종료와 관계없이 같은 제한 시간 안에서 표지·분야를 조회한다.
        FutureTask<CatalogBook> images = new FutureTask<>(() -> yes24(isbn));
        Thread.startVirtualThread(images);
        try {
            CatalogBook metadata = lookupMetadata(isbn, deadline, attempt);
            if (Thread.currentThread().isInterrupted()) {
                attempt.degraded = true;
                return metadata;
            }
            try {
                CatalogBook yes24 = await("yes24", images, deadline - System.nanoTime(), attempt);
                return merge(metadata, withoutYes24Placeholder(yes24, deadline, attempt));
            } catch (TimeoutException e) {
                attempt.degraded = true;
                return metadata;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                attempt.degraded = true;
                return metadata;
            }
        } finally {
            images.cancel(true);
        }
    }

    private CatalogBook withoutYes24Placeholder(CatalogBook book, long deadline, Attempt attempt) {
        if (book == null || book.sideCoverUrl() == null) return book;
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            attempt.degraded = true;
            return book;
        }

        FutureTask<Boolean> check = new FutureTask<>(
                () -> fetcher.isYes24SideCoverPlaceholder(book.sideCoverUrl()));
        Thread.startVirtualThread(check);
        try {
            Boolean placeholder = await(
                    "yes24 책등", check, Math.min(remaining, YES24_SIDE_COVER_TIMEOUT.toNanos()), attempt);
            if (!Boolean.TRUE.equals(placeholder)) return book;
            return new CatalogBook(
                    book.isbn(), book.title(), book.author(), book.publisher(), book.totalPages(),
                    book.genre(), book.coverUrl(), null, book.yes24ProductUrl(), book.source());
        } catch (TimeoutException e) {
            attempt.degraded = true;
            return book;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            attempt.degraded = true;
            return book;
        } finally {
            check.cancel(true);
        }
    }

    private CatalogBook lookupMetadata(String isbn, long deadline, Attempt attempt) {
        Map<String, Supplier<CatalogBook>> configured = new LinkedHashMap<>();
        if (!TextValues.isBlank(properties.nlkCertKey()))
            configured.put("nlk", () -> nlk(isbn));
        // FR-01: 국립중앙도서관은 KDC를 거의 주지 않아 도서관이 분류한 정보나루 KDC로 장르를 보완한다.
        if (!TextValues.isBlank(properties.data4libraryAuthKey()))
            configured.put("data4library", () -> data4library(isbn));
        if (!TextValues.isBlank(properties.kakaoRestApiKey()))
            configured.put("kakao", () -> kakao(isbn));
        if (!TextValues.isBlank(properties.googleBooksKey()))
            configured.put("google", () -> google(isbn));
        configured.put("openlibrary", () -> openLibrary(isbn));
        List<Map.Entry<String, Supplier<CatalogBook>>> sources = List.copyOf(configured.entrySet());

        List<FutureTask<CatalogBook>> started = new ArrayList<>();
        CatalogBook merged = null;
        try {
            long lastStart = System.nanoTime();
            for (int i = 0; i < sources.size(); i++) {
                if (started.size() <= i) {
                    if (deadline - System.nanoTime() <= 0) {
                        attempt.degraded = true;
                        return merged;
                    }
                    lastStart = start(sources.get(i), started);
                }
                CatalogBook result;
                while (true) {
                    long now = System.nanoTime();
                    boolean canHedge = started.size() < sources.size();
                    // 연결과 본문 읽기를 포함해 남은 시간만 기다리고, 오래 걸리면 다음 조회처를 함께 시작한다.
                    long wait =
                            canHedge
                                    ? Math.min(deadline - now, lastStart + HEDGE_DELAY.toNanos() - now)
                                    : deadline - now;
                    try {
                        result = await(sources.get(i).getKey(), started.get(i), wait, attempt);
                        break;
                    } catch (TimeoutException e) {
                        if (deadline - System.nanoTime() <= 0) {
                            attempt.degraded = true;
                            return mergeFinished(merged, sources, started, i, attempt);
                        }
                        lastStart = start(sources.get(started.size()), started);
                    }
                }
                merged = merge(merged, result);
                if (isComplete(merged)) return merged;
            }
            return merged;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            attempt.degraded = true;
            return merged;
        } finally {
            started.forEach(task -> task.cancel(true));
        }
    }

    private static long start(
            Map.Entry<String, Supplier<CatalogBook>> source, List<FutureTask<CatalogBook>> started) {
        FutureTask<CatalogBook> task = new FutureTask<>(source.getValue()::get);
        started.add(task);
        Thread.startVirtualThread(task);
        return System.nanoTime();
    }

    /** 제한 시간에 걸리면 앞 조회처를 기다리던 사이에 끝난 뒤 조회처 결과도 우선순위대로 합친다. */
    private static CatalogBook mergeFinished(
            CatalogBook merged,
            List<Map.Entry<String, Supplier<CatalogBook>>> sources,
            List<FutureTask<CatalogBook>> started,
            int from,
            Attempt attempt)
            throws InterruptedException {
        for (int i = from; i < started.size(); i++) {
            FutureTask<CatalogBook> task = started.get(i);
            if (!task.isDone()) continue;
            try {
                merged = merge(merged, await(sources.get(i).getKey(), task, 0, attempt));
            } catch (TimeoutException ignored) {
                // isDone을 확인한 뒤라 일어나지 않는다.
            }
        }
        return merged;
    }

    private static <T> T await(
            String source, FutureTask<T> task, long timeoutNanos, Attempt attempt)
            throws InterruptedException, TimeoutException {
        try {
            return task.get(Math.max(timeoutNanos, 0), TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) throw error;
            attempt.degraded = true;
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
