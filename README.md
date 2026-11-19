# DOKPAMINE-BE

독파민 백엔드입니다. 바코드로 책을 등록하고 독서 시간·페이지·감상을 기록하는 Android 앱의 서버입니다.

Java 21 + Spring Boot 4.1.2 프로젝트입니다. SeniorON 백엔드처럼 `domain/{기능}` 아래에
`controller`, `service`, `repository`, `entity`, `dto/request`, `dto/response`를 두고,
공용 설정·JWT·예외 처리는 `global`로 분리했습니다. [전체 패키지 구조](docs/PACKAGE_STRUCTURE.md)를 참고하세요.

## 빠른 시작

IntelliJ IDEA 또는 Spring Tools에서 이 폴더를 Gradle 프로젝트로 열고,
`DokpamineBeApplication.java`의 `main()`을 실행하면 됩니다.

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS / Linux:

```bash
chmod +x gradlew
./gradlew bootRun
```
- JDK 21이 필요합니다. Gradle은 wrapper가 내려받습니다.
- 기본 `local` 프로필은 H2 파일 DB(`./data/`)를 쓰므로 DB 설치가 필요 없습니다.
- 헬스 체크: http://localhost:8080/actuator/health

## Swagger API 문서

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- `Authorize`에 회원가입·로그인 응답의 `accessToken`을 입력하면 인증이 필요한 API를 호출할 수 있습니다.

컨트롤러의 `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`로 API 설명을 관리합니다.
문서 페이지는 로그인 없이 열 수 있고, 기존 API의 JWT 인증 규칙은 유지됩니다.

## 테스트와 빌드

```powershell
.\gradlew.bat test
.\gradlew.bat build
java -jar build/libs/dokpamine-be-0.0.1-SNAPSHOT.jar
```

운영 코드는 `src/main/java`, 테스트는 `src/test/java`에 있습니다.
빌드와 실행에는 JDK만 필요하며 Kotlin이나 Python 설치는 필요하지 않습니다.
테스트는 H2 메모리 DB를 사용하고 외부 도서 조회는 모의 응답으로 검증합니다.
인터넷 연결은 최초 Gradle 및 의존성 다운로드 시 필요합니다.

### 환경 변수

API 키, JWT 서명 키, DB 비밀번호는 프로젝트 루트의 `.env`에서 관리합니다.
처음 설정하는 환경에서는 `.env.example`을 `.env`로 복사하고 필요한 값을 입력하세요.
`.env`와 `.env.*`는 Git에서 제외되며, 실제 값이 없는 `.env.example`만 공유합니다.

`.env`는 자동으로 로딩되지 않습니다. macOS / Linux에서는 프로젝트 루트에서
다음 명령으로 환경 변수에 반영한 뒤 서버를 실행하세요. 값을 바꾸면 서버를 중지하고 다시 실행합니다.

```bash
set -a
source .env
set +a
./gradlew bootRun
```

IntelliJ에서는 실행 설정(Edit Configurations)의 Environment variables 칸 오른쪽 폴더 아이콘으로
`.env`를 지정하세요. IntelliJ는 등호가 있는 `#` 줄도 변수로 읽으므로, `.env`의 주석에는 등호를 쓰지 않습니다.
Windows에서 실행할 때는 실행 환경의 환경 변수에 동일한 값을 설정하세요.
파일에 적힌 값은 애플리케이션에 환경 변수로 전달하며, Java 코드나 `application.yml`에 실제 키를 입력하지 않습니다.

| 이름 | 용도 | 기본값 |
|---|---|---|
| `DOKPAMINE_JWT_SECRET` | JWT 서명 키(UTF-8 기준 32바이트 이상, `prod` 필수) | `local`에서만 개발용 기본값 사용 |
| `NLK_CERT_KEY` | 국립중앙도서관 ISBN 서지정보(쪽수·KDC 우선). [인증키 신청 안내](https://www.nl.go.kr/NL/contents/N31101010000.do) | 없음 |
| `KAKAO_REST_API_KEY` | 카카오 ISBN 책 검색(제목·저자·출판사·표지 보완). [REST API 키 발급 안내](https://developers.kakao.com/docs/ko/app-setting/app#key) | 없음 |
| `GOOGLE_BOOKS_KEY` | Google Books 조회. [API 키 발급 안내](https://developers.google.com/books/docs/v1/using#APIKey) | 없음 |
| `YES24_API_KEY` | YES24 ISBN 상세 조회로 앞표지·책등·상품 링크 제공. [API 키 발급 안내](https://developers.yes24.com/docs) | 없음 |
| `SPRING_PROFILES_ACTIVE=prod` + `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 운영 설정 | - |

조회 순서는 국립중앙도서관 → 카카오 → Google Books → Open Library이며, 앞 결과의 빈 칸만 뒤 결과로 채웁니다.
YES24 키가 있으면 상품 상세 조회를 병행하여 YES24 앞표지를 우선 사용하고 책등·상품 링크를 함께 반환합니다.
키가 비어 있는 조회처는 건너뜁니다. 모든 키가 없으면 Open Library만 조회하므로 국내 도서 정보가 부족하거나 제목이 로마자로 나올 수 있습니다.

## 문서
- [API 계약](docs/API.md)
- [패키지 구조](docs/PACKAGE_STRUCTURE.md)
- [Java 전환 내용](docs/MIGRATION.md)
- [에이전트 작업 규칙](AGENTS.md)

## 구현 범위 (프로토타입 v0.1)
- 회원가입·로그인(JWT)
- ISBN 도서 조회
- 오프라인 우선 기록 동기화(멱등 PUT, snapshot)
- 공개 감상 피드·좋아요
- 주간 독서왕 랭킹
