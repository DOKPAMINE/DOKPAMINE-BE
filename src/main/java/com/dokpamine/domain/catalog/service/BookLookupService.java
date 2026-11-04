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

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.*;

@Service
public class BookLookupService {
    private static final Logger log = LoggerFactory.getLogger(BookLookupService.class);
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
        String isbn = Isbn.normalize(rawIsbn);
        if (isbn == null) throw ApiException.badRequest("올바른 ISBN 이 아니에요.");
        List<CatalogBook> results = new ArrayList<>();
        if (!TextValues.isBlank(properties.nlkCertKey()))
            add(results, safe("nlk", () -> nlk(isbn)));
        if (!TextValues.isBlank(properties.kakaoRestApiKey()))
            add(results, safe("kakao", () -> kakao(isbn)));
        if (!TextValues.isBlank(properties.googleBooksKey()))
            add(results, safe("google", () -> google(isbn)));
        add(results, safe("openlibrary", () -> openLibrary(isbn)));
        if (results.isEmpty()) return null;
        CatalogBook merged = results.getFirst();
        for (int i = 1; i < results.size(); i++) {
            CatalogBook b = results.get(i);
            merged =
                    new CatalogBook(
                            isbn,
                            first(merged.title(), b.title()),
                            first(merged.author(), b.author()),
                            first(merged.publisher(), b.publisher()),
                            first(merged.totalPages(), b.totalPages()),
                            first(merged.genre(), b.genre()),
                            first(merged.coverUrl(), b.coverUrl()),
                            merged.source());
        }
        return merged;
    }

    private CatalogBook nlk(String isbn) {
        String body = fetcher.fetch("nlk", isbn);
        if (body == null) return null;
        for (JsonNode item : json.readTree(body).path("docs")) {
            if (!isbn.equals(Isbn.normalize(text(item, "EA_ISBN")))) continue;
            String author = text(item, "AUTHOR");
            if (author != null) {
                author = author.split(";", 2)[0];
                author =
                        TextValues.trimToNull(
                                author.substring(author.indexOf(':') + 1).split(",", 2)[0]);
            }
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
                    author,
                    text(item, "PUBLISHER"),
                    pages,
                    Genres.fromKdc(text(item, "KDC")),
                    https(text(item, "TITLE_URL")),
                    "nlk");
        }
        return null;
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
            if (candidate != null && !candidate.equals("기타")) {
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
                "openlibrary");
    }

    private CatalogBook safe(String source, Supplier<CatalogBook> action) {
        try {
            return action.get();
        } catch (Exception e) {
            // FR-01: 예외 메시지에는 키를 포함한 URL이 있을 수 있으므로 예외 종류만 남긴다.
            log.warn("{} 도서 조회 실패: {}", source, e.getClass().getSimpleName());
            return null;
        }
    }

    private static void add(List<CatalogBook> list, CatalogBook book) {
        if (book != null) list.add(book);
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
