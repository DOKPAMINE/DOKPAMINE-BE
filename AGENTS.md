# DOKPAMINE-BE: 에이전트 작업 규칙

독파민(독서 기록 앱)의 백엔드다. Android 앱(`DOKPAMINE-AOS`)이 유일한 클라이언트다.
요구사항 ID(FR-xx, NFR-xx, UC-xx)와 화면 번호(S-xx)는 DOKPAMINE-DOCS의 PRD.md v1.0을 따른다. Figma는 `v1.0` 페이지가 기준이다.

**브랜치, 커밋, Issue, PR 규칙은 `CONTRIBUTING.md`를 따른다.** 특히 커밋은 Conventional Commits 형식(`feat(record): …`)에 `Refs #이슈번호`를 붙이고, 커밋 하나에는 논리적으로 하나의 변경만 담는다. 리뷰 워커는 리뷰에서 고친 내용을 별도 커밋으로 남긴다.

## 스택
- Java 21 · Spring Boot 4.1 · Gradle(Groovy DSL, wrapper 포함)
- Spring Web MVC, Data JPA, Security + OAuth2 Resource Server(HS256 JWT), Validation, Flyway
- DB: 로컬은 H2 파일 DB(PostgreSQL 모드, `./data/`), 운영은 PostgreSQL(`prod` 프로필)
- JSON: Jackson 3 (`tools.jackson.*`). 공용 애너테이션은 `com.fasterxml.jackson.annotation.*`를 사용한다.

## 명령
```bash
./gradlew bootRun          # http://localhost:8080 (local 프로필, H2)
./gradlew test             # 통합 테스트 (H2 in-memory, test 프로필)
./gradlew build            # 컴파일 + 테스트
```
H2 콘솔은 http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/dokpamine`)이다.

## 구조
```
src/main/java/com/dokpamine/
  domain/{user,record,catalog,feed,ranking}/
    controller/  HTTP 요청과 응답
    service/     비즈니스 로직과 트랜잭션
    repository/  JPA 저장소와 조회 projection
    entity/      JPA 엔티티와 enum
    dto/request/ · dto/response/  전송 모델
    dto/         요청·응답 공용 기록 동기화 모델
    client/ · util/ · support/   기능별 외부 조회 및 보조 로직
  global/config/                       SecurityConfig, 설정 프로퍼티
  global/jwt/                          인증 사용자 ID
  global/apiPayload/exception/         ApiException, GlobalExceptionHandler
  global/apiPayload/response/          ErrorResponse
src/main/resources/db/migration/       Flyway SQL
docs/API.md                            기존 API 계약

```

## 반드시 지킬 규칙
1. **API 계약 우선.** 엔드포인트, DTO 필드, 상태 코드를 바꾸면 `docs/API.md`를 같은 커밋에서 고치고, PR 설명에 "AOS 영향 있음"을 적는다.
2. **스키마는 Flyway로만 바꾼다.** 이미 머지된 `V*.sql`은 절대 수정하지 않고 새 `V{n}__설명.sql`을 추가한다. `ddl-auto`는 `validate`로 둔다.
3. **개인 기록 PUT은 멱등을 유지한다.** 클라이언트 UUID가 PK이고, 재전송해도 1건만 남아야 한다(NFR-05). 소유자 검사(403)를 빼지 않는다.
4. **비공개 권한은 서버에서 강제한다.** 다른 사용자의 비공개 감상은 목록·상세 모두 404다(NFR-09). 클라이언트에서 숨기는 방식에 의존하지 않는다.
5. 사용자에게 보이는 오류 메시지는 한국어 존댓말 한 문장으로 쓴다(`ApiException`).
6. 외부 API 키(국립중앙도서관, 카카오, Google)와 JWT 시크릿은 환경 변수로만 받는다. 키를 커밋하지 않는다.
7. 새 기능에는 `src/test`에 MockMvc 통합 테스트를 추가한다. 테스트 이름은 요구사항 ID를 넣은 한국어 문장으로 쓴다.

## 코드 스타일
- 4칸 들여쓰기(`.editorconfig`), 패키지는 기능 단위로 나눈다.
- 엔티티는 Java `class` + JPA 기본 생성자, 전송 모델은 Java `record` + Bean Validation으로 쓴다. Controller는 요청 처리, Service는 비즈니스 로직과 트랜잭션을 맡는다.
- 주석은 "왜"를 한국어로 짧게 쓴다. 요구사항 ID를 붙이면 리뷰가 쉬워진다.

## 이번 학기 범위 밖 (구현하지 않음)
캐릭터 성장, 독서 지도, AI 추천, 댓글·대댓글, 신고·차단, 칭호
