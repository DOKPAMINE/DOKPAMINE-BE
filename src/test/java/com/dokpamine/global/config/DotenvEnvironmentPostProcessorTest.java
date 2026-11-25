package com.dokpamine.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class DotenvEnvironmentPostProcessorTest {
    private final DotenvEnvironmentPostProcessor processor =
            new DotenvEnvironmentPostProcessor(new DeferredLogs());

    @TempDir Path dir;

    @Test
    @DisplayName("FR-01 .env의 따옴표·주석·export 형식을 셸에서 source한 값과 같게 읽는다")
    void readsShellStyleValues() throws IOException {
        Path file =
                write(
                        """
                        # 국립중앙도서관 인증키
                        NLK_CERT_KEY='nlk-key'
                        KAKAO_REST_API_KEY="kakao-key"
                        export YES24_API_KEY=yes24-key
                        DATA4LIBRARY_AUTH_KEY=d4l-key # 정보나루

                        NOT_A_PAIR
                        """);

        assertEquals(
                Map.of(
                        "NLK_CERT_KEY", "nlk-key",
                        "KAKAO_REST_API_KEY", "kakao-key",
                        "YES24_API_KEY", "yes24-key",
                        "DATA4LIBRARY_AUTH_KEY", "d4l-key"),
                DotenvEnvironmentPostProcessor.read(file));
    }

    @Test
    @DisplayName("FR-01 .env 값은 가장 낮은 우선순위라 같은 이름의 환경 변수·실행 인자가 이긴다")
    void existingPropertiesWin() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("args", Map.of("GOOGLE_BOOKS_KEY", "from-args")));

        processor.apply(environment, write("GOOGLE_BOOKS_KEY=from-dotenv\nNLK_CERT_KEY=nlk\n"));

        assertEquals("from-args", environment.getProperty("GOOGLE_BOOKS_KEY"));
        assertEquals("nlk", environment.getProperty("NLK_CERT_KEY"));
        assertEquals(
                DotenvEnvironmentPostProcessor.SOURCE_NAME,
                environment.getPropertySources().stream().toList().getLast().getName());
    }

    @Test
    @DisplayName("FR-01 test 프로필이거나 .env가 없으면 설정을 바꾸지 않는다")
    void skipsTestProfileAndMissingFile() throws IOException {
        StandardEnvironment testing = new StandardEnvironment();
        testing.setActiveProfiles("test");
        processor.apply(testing, write("NLK_CERT_KEY=nlk\n"));
        assertNull(testing.getProperty("NLK_CERT_KEY"));

        StandardEnvironment local = new StandardEnvironment();
        processor.apply(local, dir.resolve("missing.env"));
        assertNull(local.getPropertySources().get(DotenvEnvironmentPostProcessor.SOURCE_NAME));
    }

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve(".env"), content, StandardCharsets.UTF_8);
    }
}
