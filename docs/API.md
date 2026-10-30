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
조회 순서는 알라딘(`ALADIN_TTB_KEY`) → Google Books(`GOOGLE_BOOKS_KEY`) → Open Library(키 불필요)다.

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

## 라운지: 공개 감상 (FR-08, FR-09)

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/feed` | `FeedItem[]` (공개 감상 최신순 100개) |
| GET | `/api/feed/{reviewId}` | `FeedItem` · 비공개/삭제/없음은 모두 404 |
| POST | `/api/feed/{reviewId}/like` | `{likeCount, likedByMe: true}` (중복 요청해도 1회) |
| DELETE | `/api/feed/{reviewId}/like` | `{likeCount, likedByMe: false}` |

`FeedItem = {id, nickname, bookTitle, bookAuthor, genre, oneLine?, body?, publishedAt?, likeCount, likedByMe, mine}`.
공개 응답에는 닉네임, 책 제목·저자, 감상만 담는다. 독서 시간, 사진, 보관 위치는 노출하지 않는다.

## 독서왕 랭킹 (PRD L01~L05)

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/ranking/weekly?weekStart=YYYY-MM-DD` | `{weekStart, participating, entries: [{rank, userId, nickname, seconds, me}]}` |

집계 기준은 다음과 같다.
- 기간: KST 월요일 00:00부터 다음 월요일 00:00 직전까지
- 대상: `rankingOptIn` 사용자의 TIMER 세션만
- 상한: 하루 180분
- 동점: 1, 2, 2, 4 방식
