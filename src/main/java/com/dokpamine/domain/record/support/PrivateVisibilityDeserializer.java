package com.dokpamine.domain.record.support;

import com.dokpamine.domain.record.entity.Visibility;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 비공개 기본값은 필드가 생략된 요청에만 적용한다(NFR-09). */
public final class PrivateVisibilityDeserializer extends ValueDeserializer<Visibility> {
    @Override
    public Visibility deserialize(JsonParser parser, DeserializationContext context) {
        return context.readValue(parser, Visibility.class);
    }

    @Override
    public Visibility getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(Visibility.class, "visibility must not be null");
    }

    @Override
    public Visibility getAbsentValue(DeserializationContext context) {
        return Visibility.PRIVATE;
    }
}
