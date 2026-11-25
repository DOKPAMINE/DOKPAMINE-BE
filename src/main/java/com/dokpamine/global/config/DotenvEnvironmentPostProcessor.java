package com.dokpamine.global.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-01: 실행 폴더의 .env를 설정 값으로 읽는다. source나 IDE 설정 없이 bootRun만 해도 도서 조회 키가 빠지지 않게 하기
 * 위해서다. OS 환경 변수와 실행 인자가 우선하도록 가장 낮은 우선순위로 붙이고, 로컬 키에 따라 결과가 달라지지 않게 test
 * 프로필에서는 읽지 않는다.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String SOURCE_NAME = "dotenv";
    private final Log log;

    public DotenvEnvironmentPostProcessor(DeferredLogFactory logs) {
        this.log = logs.getLog(DotenvEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        apply(environment, Path.of(".env"));
    }

    void apply(ConfigurableEnvironment environment, Path file) {
        if (environment.acceptsProfiles(Profiles.of("test")) || !Files.isRegularFile(file)) return;
        Map<String, Object> values = read(file);
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, values));
        // 키 값은 로그에 남기지 않는다.
        log.info(file.toAbsolutePath().normalize() + "에서 설정 " + values.size() + "개를 읽었습니다.");
    }

    static Map<String, Object> read(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(".env 파일을 읽지 못했습니다.", e);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("﻿")) line = line.substring(1).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring("export ".length()).strip();
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            String key = line.substring(0, equals).strip();
            values.put(key, value(line.substring(equals + 1).strip()));
        }
        return values;
    }

    // 셸에서 source할 때와 같은 값이 되도록 감싼 따옴표를 벗기고, 따옴표 없는 값의 " #" 뒤는 주석으로 본다.
    private static String value(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            if ((first == '\'' || first == '"') && value.charAt(value.length() - 1) == first)
                return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment >= 0 ? value.substring(0, comment).strip() : value;
    }

    @Override
    public int getOrder() {
        // 프로필이 정해진 뒤에 test 여부를 판단한다.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
