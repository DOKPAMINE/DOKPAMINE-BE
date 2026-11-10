# 독파민 API 계약

BE와 AOS가 공유하는 단일 계약 문서다. **API를 바꾸면 이 문서와 AOS의 `data/remote/ApiModels.kt`를 같은 PR 흐름에서 함께 고친다.**

- Base URL: 로컬 `http://localhost:8080/` (에뮬레이터에서는 `http://10.0.2.2:8080/`)
- 인증: `Authorization: Bearer <accessToken>` (회원가입·로그인 제외 전부 필요)
- 시각: ISO-8601 UTC 문자열 (`2026-10-29T16:30:00Z`)
- ID: 클라이언트가 만든 UUID (오프라인 우선, 아래 "동기화 규칙" 참고)
- 오류: `{ "code": "VALIDATION_FAILED", "message": "사용자에게 보여줄 한국어 문장" }`

## Swagger 문서

- UI: `http://localhost:8080/swagger-ui/index.html` (`/swagger-ui.html`도 지원)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 문서 조회는 인증 없이 가능하다. 인증이 필요한 API는 `Authorize`에 accessToken을 입력하여 호출한다.
- 회원가입·로그인을 제외한 기존 API의 JWT 인증 규칙과 응답 계약은 동일하다.

## 인증 (FR-10)

| Method | Path | Body | 응답 |
|---|---|---|---|
| POST | `/api/auth/signup` | `{email, password(8~64), nickname(1~30)}` | 201 `TokenResponse` · 409 `EMAIL_TAKEN` |
| POST | `/api/auth/login` | `{email, password}` | 200 `TokenResponse` · 401 `INVALID_CREDENTIALS` |
| GET | `/api/me` | - | `MeResponse` |
| PATCH | `/api/me` | `{nickname?, rankingOptIn?}` | `MeResponse` |

`TokenResponse = {accessToken, expiresAt, user: MeResponse}`, `MeResponse = {id, email, nickname, rankingOptIn}`. 토큰 유효기간은 14일이고 리프레시 토큰은 없다(만료되면 재로그인).

## 도서 조회 (FR-01)

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/catalog/isbn/{isbn}` | `CatalogBook` · 400 잘못된 ISBN · 404 정보 없음 |
| GET | `/api/catalog/genres` | `["소설","SF",…,"기타"]` |

`CatalogBook = {isbn, title?, author?, publisher?, totalPages?, genre?, coverUrl?, source}`. 일부 필드가 비어 있을 수 있으며, 앱은 빈 칸을 사용자 입력으로 채운다.
조회 순서는 국립중앙도서관(`NLK_CERT_KEY`, 쪽수·KDC 우선) → 카카오(`KAKAO_REST_API_KEY`, ISBN 조회로 제목·저자·출판사·표지 보완) → Google Books(`GOOGLE_BOOKS_KEY`) → Open Library(키 불필요)다.
키가 비어 있는 조회처는 호출하지 않고, 앞 결과의 빈 칸만 뒤 결과로 채운다. 외부 조회 실패 시에도 다음 조회처로 넘어간다.
`source` 가능 값은 `nlk|kakao|google|openlibrary`이며, 병합에 사용된 첫 결과의 값을 유지한다.

## 개인 기록 동기화 (NFR-04, NFR-05)

| Method | Path | Body = 응답 |
|---|---|---|
| PUT | `/api/books/{id}` | `BookDto` |
| PUT | `/api/sessions/{id}` | `SessionDto` |
| PUT | `/api/reviews/{id}` | `ReviewDto` |
| PUT | `/api/quotes/{id}` | `QuoteDto` |
| GET | `/api/sync/snapshot` | `{books, sessions, reviews, quotes}` (삭제 안 된 것만) |

**동기화 규칙**
1. 모든 PUT은 멱등이다. 같은 `id`로 몇 번을 보내도 서버에는 한 건만 남는다.
2. 삭제는 `deleted: true`로 PUT한다(tombstone).
3. 다른 사용자의 `id`이면 403을 돌려준다.
4. 세션·감상·문장이 서버에 아직 없는 책을 참조하면 409 `BOOK_NOT_SYNCED`를 돌려준다. 클라이언트는 책 → 세션 → 감상 → 문장 순서로 올리고, `BOOK_NOT_SYNCED`이면 나중에 다시 시도한다.
5. 401이 오면 클라이언트는 대기 기록을 그대로 둔 채 재로그인을 안내한다.
6. 새 세션은 요청의 `source`로 저장한다. 이미 저장된 세션은 저장된 값이 MANUAL이거나, 요청이 MANUAL이거나, 시간(`startedAt`, `endedAt`, `activeSeconds`)이 바뀌면 MANUAL로 저장하고, 그 밖에는 TIMER로 저장한다. 즉 서버는 TIMER로 올리는 것만 막고 MANUAL로 낮추는 요청은 받아들이며, MANUAL은 TIMER로 되돌릴 수 없다. 페이지(`startPage`, `endPage`), `deleted`, `bookId`만 바뀌는 것은 시간 수정으로 보지 않으므로, 요청이 TIMER면 TIMER를 유지한다. 같은 내용의 재전송도 시간 수정으로 보지 않으며, 시각은 DB와 같은 마이크로초 정밀도로 비교한다. 응답의 `source`는 저장된 값이다.

```text
BookDto    {id, isbn?, title(1~200), author(1~100), publisher?, totalPages(≥1), genre, coverUrl?,
            status: WANT|READING|DONE, currentPage(≥0, ≤totalPages), completedCount, completedAt?,
            deleted, createdAt, updatedAt}
SessionDto {id, bookId, startedAt, endedAt, activeSeconds, startPage, endPage?(≤ book.totalPages),
            source: TIMER|MANUAL, deleted, createdAt}
ReviewDto  {id, bookId, oneLine?(≤100), body?(≤5000), visibility: PRIVATE|PUBLIC, deleted, createdAt, updatedAt, version?}
           // oneLine 과 body 중 하나는 있어야 함. 기본값 PRIVATE (NFR-09)
QuoteDto   {id, bookId, text(1~1000), page?, memo?(≤500), deleted, createdAt, updatedAt}
```

### 감상 버전과 충돌 처리 (NFR-05, NFR-09)

- 새 감상의 PUT 요청에서는 `version`을 생략하거나 `null`로 보낸다. 생성된 감상과 마이그레이션 전 감상의 최초 서버 버전은 `0`이다. PUT 성공 응답과 본인 스냅샷의 `reviews`에는 현재 서버 버전이 포함된다.
- 앱은 서버 버전을 로컬에 저장하고, 감상의 수정·삭제 요청에는 **편집을 시작할 때 기준으로 삼은 버전**을 함께 보낸다. 서버는 현재 버전과 일치할 때 변경을 저장하고 버전을 올린다. `updatedAt`은 충돌 판정의 순서 기준으로 사용하지 않는다.
- 정규화한 수정 가능 필드와 마이크로초 정밀도의 `updatedAt`이 이미 저장된 값과 같으면 200과 현재 버전을 반환한다. 서버 버전은 그대로 유지된다.
- 저장 상태를 바꾸려는 요청에서 버전이 누락되었거나 일치하지 않으면 `409 REVIEW_VERSION_CONFLICT`를 반환한다. 존재하지 않는 감상에 버전을 보낸 경우와 같은 감상에 대한 동시 쓰기가 충돌한 경우에도 같은 오류를 반환한다.
- Android는 충돌한 요청의 자동 재시도를 멈추고 로컬 편집 내용을 초안으로 보존한다. `GET /api/sync/snapshot`으로 서버 상태를 가져와 사용자에게 보여 준다. 스냅샷에 감상이 없으면 삭제된 상태로 처리한다. 감상이 남아 있으면 사용자가 최신 내용과 공개 여부를 확인하고 다시 저장할 때 최신 버전으로 새 PUT을 만든다. 충돌한 요청의 버전만 바꿔 자동 재전송하면 비공개 상태를 덮어쓸 수 있다.
- 버전 필드가 없는 구버전 앱도 신규 생성은 가능하지만 기존 감상을 수정하거나 삭제하려면 앱 업데이트가 필요하다.

## 라운지: 공개 감상 (FR-08, FR-09, FR-16)

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/feed` | `FeedItem[]` (공개 감상 최신순 100개) |
| GET | `/api/feed/books?query=데미안` | `BookReviewGroup[]` (책 이름 검색, 묶음별 공개 감상 최신순) |
| GET | `/api/feed/{reviewId}` | `FeedItem` · 비공개/삭제/없음은 모두 404 |
| POST | `/api/feed/{reviewId}/like` | `{likeCount, likedByMe: true}` (중복 요청해도 1회) |
| DELETE | `/api/feed/{reviewId}/like` | `{likeCount, likedByMe: false}` |

`FeedItem = {id, nickname, bookTitle, bookAuthor, genre, oneLine?, body?, publishedAt?, likeCount, likedByMe, mine}`.
공개 응답에는 닉네임, 책 제목·저자, 감상만 담는다. 독서 시간, 사진, 보관 위치는 노출하지 않는다.

### 책별 공개 감상 검색 (FR-16, NFR-09)

`GET /api/feed/books`는 인증이 필요하며 `query`에 책 제목의 일부를 전달한다(URL 인코딩).
검색어와 제목은 앞뒤 공백 제거, 연속 공백(탭·줄바꿈·유니코드 공백 포함)을 한 칸으로 축약하고 대소문자를 무시하여 비교한다.
검색 대상은 서버에 저장된 책이며 외부 도서 조회를 호출하지 않는다.
공개 감상이 있는 책 중 제목 LIKE 조건을 통과한 후보를 최신 공개 감상 시각 내림차순(시각이 없으면 마지막), 동점은 책 UUID 문자열 오름차순으로 최대 500건 읽고 제목을 정규화해 재검증하므로, 후보 상한을 초과한 책은 검색에서 제외될 수 있다. 이후 요약 조회도 일치 ISBN(ISBN-10/13 사본 포함) 또는 제목 LIKE 조건에 해당하는 책으로 제한한다.

```text
BookReviewGroup {bookKey, bookTitle, bookAuthor, isbn?, reviews: FeedItem[]}
```

- ISBN이 있으면 앞뒤 공백을 제거하고 유효한 ISBN-10/13을 ISBN-13으로 정규화해 같은 책끼리 묶는다. 정규화할 수 없는 ISBN은 공백 제거 값 그대로 사용한다. 키는 `isbn:<ISBN>`이며 응답의 `isbn`도 이 값을 사용한다.
- ISBN이 null 또는 공백이면 정규화한 제목과 저자가 **모두** 같은 책끼리 묶는다. 키는 `text:<Base64URL>`이며, 정규화한 제목 + 줄바꿈 + 저자의 UTF-8 바이트를 패딩 없이 인코딩한다.
- ISBN이 다른 책, ISBN이 있는 책과 없는 책은 제목·저자가 같아도 별도 묶음이다. 앱은 `bookKey`를 묶음 식별자로 사용한다.
- 묶음의 공개 감상에 연결된 책 제목 중 하나라도 검색어와 일치하면 그 묶음을 반환한다. ISBN이 같고 제목 표기가 다른 감상도 포함한다.
- `PUBLIC`이고 삭제되지 않은 감상만 포함하며, 삭제된 책의 감상도 제외한다. 본인의 비공개 감상 역시 포함하지 않는다.
- `reviews`는 `publishedAt` 내림차순(시각이 없으면 마지막), 동점은 감상 UUID 문자열 오름차순이다. 묶음도 가장 최신 감상의 시각과 UUID 문자열 순서이며, 대표 제목·저자는 그 감상의 책에서 가져온다.
- 각 감상은 기존 `FeedItem`과 같은 공개 필드만 담는다. 검색 결과는 위 정렬 순서대로 최대 20개 묶음이며, 묶음마다 최신 감상 최대 100개를 반환한다.
- 일치하는 공개 감상이 없으면 `200 []`이다.
- `query` 누락·빈 문자열·공백만 입력하면 `400 {"code":"BAD_REQUEST","message":"검색어를 입력해 주세요."}`이다.
- 토큰 누락·만료·무효는 기존 인증 규칙대로 `401`이다.

## 독서왕 랭킹 (FR-15, S-73~S-75)

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/ranking/weekly?weekStart=YYYY-MM-DD` | `{weekStart, participating, top: RankingEntry[], around: RankingEntry[]}` |

`RankingEntry = {rank, userId, nickname, seconds, me}`. `rank`는 전체 참여자 중 순위(1부터 연속), `seconds`는 상한 적용 후 주간 초, `me`는 요청자 여부다. AOS 영향 있음: 기존 `entries`를 `top`과 `around`로 대체하며 AOS 8단계 S-73에서 이 계약을 사용한다.

집계 기준은 다음과 같다.
- 기간: KST 월요일 00:00부터 다음 월요일 00:00 미만에 **시작한** 세션이다. `weekStart` 생략 시 이번 주, 지정 시 해당 날짜가 속한 주 월요일로 정규화한다.
- 대상: 현재 `rankingOptIn=true`인 사용자의 삭제되지 않은 TIMER 세션만 집계한다. MANUAL·삭제 세션·비참여자는 제외하고 참여 중단은 다음 조회부터 즉시 반영한다. 주간 인정 시간이 0초이면 순위에서 제외한다.
- 상한: 세션 시작일(KST)별 하루 180분(10,800초)이다. 세션을 종료 시각 오름차순으로 더하며 일별 상한을 넘는 시간은 버린다.
- **도달 시각**: 상한 적용 후 주간 누적 시간을 마지막으로 늘린 세션의 `endedAt`이다. 일시정지 구간 정보가 없으므로 세션 중간 시각은 추정하지 않는다. 상한 이후 세션·0초 세션은 시각을 늦추지 않으며 서버 수신·생성 시각은 사용하지 않는다.
- 동점: 주간 초 내림차순 → 도달 시각 오름차순 → 사용자 UUID 문자열 오름차순이다. 공동 순위 없이 1, 2, 3, …으로 매긴다. A 300분, B 200분, C 200분이고 B가 먼저 도달했으면 A 1위, B 2위, C 3위다.
- `top`: 전체 1~30위(참여자가 적으면 존재하는 순위만)다.
- `around`: 본인이 31위 이하일 때 본인 포함 30명이다. 원칙적으로 위 15명·아래 14명이며 끝에서 부족한 인원은 반대쪽에서 채운다. `top`과 겹칠 수 있고, 각 항목의 `rank`는 전체 순위를 유지한다. 45명 중 본인이 38위면 16~45위를 반환한다.
- 본인이 30위 이내이거나 순위가 없으면 `around=[]`다. `participating`은 요청자의 참여 동의 상태이며 비참여자는 `false`, `around=[]`지만 `top`은 조회할 수 있다. 참여에 동의했어도 주간 TIMER 시간이 0초라면 `participating=true`, `around=[]`다.
