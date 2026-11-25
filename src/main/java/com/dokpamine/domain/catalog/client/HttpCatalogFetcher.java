package com.dokpamine.domain.catalog.client;

import com.dokpamine.global.config.CatalogProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class HttpCatalogFetcher implements CatalogFetcher {
    private static final Pattern YES24_SIDE_COVER_PATH = Pattern.compile("/goods/[0-9]+/SIDE/[A-Z]+");
    private static final int YES24_PLACEHOLDER_SIZE = 2858;
    private static final String YES24_PLACEHOLDER_SHA256 =
            "8ce49c510667331380923946071031f929a28b028fe337d095861f6d840d0a6e";

    private final CatalogProperties properties;
    private final RestClient http;

    public HttpCatalogFetcher(CatalogProperties properties) {
        this.properties = properties;
        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(properties.timeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String fetch(String source, String isbn) {
        RestClient.RequestHeadersSpec<?> request =
                switch (source) {
                    case "nlk" ->
                            http.get()
                                    .uri(
                                            "https://www.nl.go.kr/seoji/SearchApi.do?cert_key={key}&result_style=json&page_no=1&page_size=1&isbn={isbn}",
                                            properties.nlkCertKey(),
                                            isbn);
                    case "data4library" ->
                            http.get()
                                    .uri(
                                            "https://data4library.kr/api/srchDtlList?authKey={key}&isbn13={isbn}&format=json",
                                            properties.data4libraryAuthKey(),
                                            isbn);
                    case "kakao" ->
                            http.get()
                                    .uri(
                                            "https://dapi.kakao.com/v3/search/book?target=isbn&query={isbn}",
                                            isbn)
                                    .header(
                                            "Authorization",
                                            "KakaoAK " + properties.kakaoRestApiKey());
                    case "google" ->
                            http.get()
                                    .uri(
                                            "https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}&key={key}",
                                            isbn,
                                            properties.googleBooksKey());
                    case "yes24" ->
                            http.get()
                                    .uri(
                                            "https://apis.yes24.com/v1/goods/itemDetail?searchType=ISBN13&query={isbn}&detail=Y",
                                            isbn)
                                    .header("X-Api-Key", properties.yes24ApiKey());
                    case "openlibrary" ->
                            http.get()
                                    .uri(
                                            "https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data",
                                            isbn);
                    default -> throw new IllegalArgumentException("지원하지 않는 도서 조회처입니다.");
                };
        return request.retrieve().body(String.class);
    }

    @Override
    public boolean isYes24SideCoverPlaceholder(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"image.yes24.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || uri.getRawUserInfo() != null
                || !YES24_SIDE_COVER_PATH.matcher(uri.getRawPath()).matches()) {
            return false;
        }

        // 미등록 책등도 같은 상품별 URL에서 200을 반환하고 ETag가 없을 수 있다.
        // 한 바이트 더 요청해 실제 이미지의 앞부분만 대체 이미지로 오인하지 않는다.
        return http.get()
                .uri("https://image.yes24.com" + uri.getRawPath())
                .header(HttpHeaders.RANGE, "bytes=0-" + YES24_PLACEHOLDER_SIZE)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()
                            || response.getHeaders().getContentLength() != YES24_PLACEHOLDER_SIZE) {
                        return false;
                    }
                    byte[] image = response.getBody().readNBytes(YES24_PLACEHOLDER_SIZE);
                    try {
                        byte[] digest = MessageDigest.getInstance("SHA-256").digest(image);
                        return YES24_PLACEHOLDER_SHA256.equals(HexFormat.of().formatHex(digest));
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }
}
