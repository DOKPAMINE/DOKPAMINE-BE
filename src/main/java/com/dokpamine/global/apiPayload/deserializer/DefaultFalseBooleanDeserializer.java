package com.dokpamine.global.apiPayload.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 삭제 필드의 생략만 기본값으로 처리하고 명시적인 null은 거절한다. */
public final class DefaultFalseBooleanDeserializer extends ValueDeserializer<Boolean> {
    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) {
        return context.readValue(parser, Boolean.class);
    }

    @Override
    public Boolean getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(Boolean.class, "deleted must not be null");
    }

    @Override
    public Boolean getAbsentValue(DeserializationContext context) {
        return false;
    }
}
