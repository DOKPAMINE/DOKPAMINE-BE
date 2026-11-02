# 독파민 API 계약

BE와 AOS가 공유하는 단일 계약 문서다. **API를 바꾸면 이 문서와 AOS의 `data/remote/ApiModels.kt`를 같은 PR 흐름에서 함께 고친다.**

- Base URL: 로컬 `http://localhost:8080/` (에뮬레이터에서는 `http://10.0.2.2:8080/`)
- 인증: `Authorization: Bearer <accessToken>` (회원가입·로그인 제외 전부 필요)
- 시각: ISO-8601 UTC 문자열 (`2026-10-29T16:30:00Z`)
- ID: 클라이언트가 만든 UUID (오프라인 우선, 아래 "동기화 규칙" 참고)
- 오류: `{ "code": "VALIDATION_FAILED", "message": "사용자에게 보여줄 한국어 문장" }`

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
4. 세션·감상·문장이 서버에 아직 없는 책을 참조하면 409 `BOOK_NOT_SYNCED`를 돌려준다. 클라이언트는 책 → 세션 → 감상 → 문장 순서로 올리고, 409이면 나중에 다시 시도한다.
5. 401이 오면 클라이언트는 대기 기록을 그대로 둔 채 재로그인을 안내한다.

```text
BookDto    {id, isbn?, title(1~200), author(1~100), publisher?, totalPages(≥1), genre, coverUrl?,
            status: WANT|READING|DONE, currentPage(≥0, ≤totalPages), completedCount, completedAt?,
            deleted, createdAt, updatedAt}
SessionDto {id, bookId, startedAt, endedAt, activeSeconds, startPage, endPage?(≤ book.totalPages),
            source: TIMER|MANUAL, deleted, createdAt}
ReviewDto  {id, bookId, oneLine?(≤100), body?(≤5000), visibility: PRIVATE|PUBLIC, deleted, createdAt, updatedAt}
           // oneLine 과 body 중 하나는 있어야 함. 기본값 PRIVATE (NFR-09)
QuoteDto   {id, bookId, text(1~1000), page?, memo?(≤500), deleted, createdAt, updatedAt}
```

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

```text
BookReviewGroup {bookKey, bookTitle, bookAuthor, isbn?, reviews: FeedItem[]}
```

- ISBN이 있으면 앞뒤 공백을 제거하고 유효한 ISBN-10/13을 ISBN-13으로 정규화해 같은 책끼리 묶는다. 정규화할 수 없는 ISBN은 공백 제거 값 그대로 사용한다. 키는 `isbn:<ISBN>`이며 응답의 `isbn`도 이 값을 사용한다.
- ISBN이 null 또는 공백이면 정규화한 제목과 저자가 **모두** 같은 책끼리 묶는다. 키는 `text:<Base64URL>`이며, 정규화한 제목 + 줄바꿈 + 저자의 UTF-8 바이트를 패딩 없이 인코딩한다.
- ISBN이 다른 책, ISBN이 있는 책과 없는 책은 제목·저자가 같아도 별도 묶음이다. 앱은 `bookKey`를 묶음 식별자로 사용한다.
- 묶음의 공개 감상에 연결된 책 제목 중 하나라도 검색어와 일치하면 그 묶음의 공개 감상 전체를 반환한다. ISBN이 같고 제목 표기가 다른 감상도 포함한다.
- `PUBLIC`이고 삭제되지 않은 감상만 포함하며, 삭제된 책의 감상도 제외한다. 본인의 비공개 감상 역시 포함하지 않는다.
- `reviews`는 `publishedAt` 내림차순(시각이 없으면 마지막), 동점은 감상 UUID 문자열 오름차순이다. 묶음도 가장 최신 감상의 시각과 UUID 문자열 순서이며, 대표 제목·저자는 그 감상의 책에서 가져온다.
- 각 감상은 기존 `FeedItem`과 같은 공개 필드만 담는다. 검색 결과에는 100개 제한을 적용하지 않는다.
- 일치하는 공개 감상이 없으면 `200 []`이다.
- `query` 누락·빈 문자열·공백만 입력하면 `400 {"code":"BAD_REQUEST","message":"검색어를 입력해 주세요."}`이다.
- 토큰 누락·만료·무효는 기존 인증 규칙대로 `401`이다.

## 독서왕 랭킹 (PRD L01~L05)

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/ranking/weekly?weekStart=YYYY-MM-DD` | `{weekStart, participating, entries: [{rank, userId, nickname, seconds, me}]}` |

집계 기준은 다음과 같다.
- 기간: KST 월요일 00:00부터 다음 월요일 00:00 직전까지
- 대상: `rankingOptIn` 사용자의 TIMER 세션만
- 상한: 하루 180분
- 동점: 1, 2, 2, 4 방식
