package com.dokpamine.domain.catalog.client;

import com.dokpamine.global.config.CatalogProperties;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Component
public class HttpCatalogFetcher implements CatalogFetcher {
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
                    case "openlibrary" ->
                            http.get()
                                    .uri(
                                            "https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data",
                                            isbn);
                    default -> throw new IllegalArgumentException("지원하지 않는 도서 조회처입니다.");
                };
        return request.retrieve().body(String.class);
    }
}
