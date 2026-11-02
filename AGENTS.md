# DOKPAMINE-BE: 에이전트 작업 규칙

독파민(독서 기록 앱)의 백엔드다. Android 앱(`DOKPAMINE-AOS`)이 유일한 클라이언트다.
요구사항 ID(FR-xx, NFR-xx, UC-xx)는 팀의 요구사항 분석서를, 화면 ID(H01, B01 …)는 Figma "독파민 · 75 화면"을 따른다.

**브랜치, 커밋, Issue, PR 규칙은 `CONTRIBUTING.md`를 따른다.** 특히 커밋은 Conventional Commits 형식(`feat(record): …`)에 `Refs #이슈번호`를 붙이고, 커밋 하나에는 논리적으로 하나의 변경만 담는다. 리뷰 워커는 리뷰에서 고친 내용을 별도 커밋으로 남긴다.

## 스택
- Kotlin 2.3 · Spring Boot 4.1 · Java 21 · Gradle(Kotlin DSL, wrapper 포함)
- Spring Web MVC, Data JPA, Security + OAuth2 Resource Server(HS256 JWT), Validation, Flyway
- DB: 로컬은 H2 파일 DB(PostgreSQL 모드, `./data/`), 운영은 PostgreSQL(`prod` 프로필)
- JSON: Jackson 3 (`tools.jackson.*` 패키지. `com.fasterxml.jackson`이 아니다)

## 명령
```bash
./gradlew bootRun          # http://localhost:8080 (local 프로필, H2)
./gradlew test             # 통합 테스트 (H2 in-memory, test 프로필)
./gradlew build            # 컴파일 + 테스트
```
H2 콘솔은 http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/dokpamine`)이다.

## 구조
```
src/main/kotlin/com/dokpamine/
  config/   SecurityConfig(JWT, 공개 경로), AppProperties
  common/   ApiException + GlobalExceptionHandler  → {code, message}
  user/     User, 회원가입·로그인·내 정보 (AuthController)
  record/   UserBook·ReadingSession·Review·Quote 엔티티, 멱등 upsert (RecordService), snapshot
  catalog/  ISBN 조회 (국립중앙도서관 → 카카오 → Google Books → Open Library), ISBN 정규화, KDC·장르 매핑
  feed/     공개 감상 피드, 좋아요 (ReviewLike)
  ranking/  주간 랭킹 집계
src/main/resources/db/migration/   Flyway SQL (V1__init.sql …)
docs/API.md                        API 계약: 엔드포인트를 바꾸면 반드시 함께 수정
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
- 엔티티는 `class` + `var`(JPA), 전송 모델은 `data class` + Bean Validation으로 쓴다.
- 주석은 "왜"를 한국어로 짧게 쓴다. 요구사항 ID를 붙이면 리뷰가 쉬워진다.

## 이번 학기 범위 밖 (구현하지 않음)
캐릭터 성장, 독서 지도, AI 추천, 댓글·대댓글, 신고·차단, 칭호
