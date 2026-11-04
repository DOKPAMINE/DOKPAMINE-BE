package com.dokpamine.domain.catalog.client;

@FunctionalInterface
public interface CatalogFetcher {
    String fetch(String source, String isbn);
}
