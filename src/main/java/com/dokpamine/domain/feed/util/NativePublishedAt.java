package com.dokpamine.domain.feed.util;

import java.sql.Timestamp;
import java.time.*;

public final class NativePublishedAt {
    private NativePublishedAt() {}

    public static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime date) return date.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalArgumentException("지원하지 않는 공개 시각 타입: " + value.getClass().getName());
    }
}
