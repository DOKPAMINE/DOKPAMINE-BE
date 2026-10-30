# DOKPAMINE-BE

독파민 백엔드입니다. 바코드로 책을 등록하고 독서 시간·페이지·감상을 기록하는 Android 앱의 서버입니다.

## 빠른 시작
```bash
./gradlew bootRun
```
- JDK 21이 필요합니다. Gradle은 wrapper가 내려받습니다.
- 기본 `local` 프로필은 H2 파일 DB(`./data/`)를 쓰므로 DB 설치가 필요 없습니다.
- 헬스 체크: http://localhost:8080/actuator/health

### 환경 변수
| 이름 | 용도 | 기본값 |
|---|---|---|
| `DOKPAMINE_JWT_SECRET` | JWT 서명 키(32바이트 이상) | 개발용 값(운영에서는 반드시 교체) |
| `ALADIN_TTB_KEY` | 알라딘 도서 조회. **국내 도서 품질을 위해 권장** | 없음 |
| `GOOGLE_BOOKS_KEY` | Google Books 조회 | 없음 |
| `SPRING_PROFILES_ACTIVE=prod` + `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 운영 설정 | - |

키가 없으면 Open Library만 조회합니다. 해외 도서는 잘 찾지만, 국내 도서는 제목이 로마자로 나오는 경우가 많습니다.

## 문서
- [API 계약](docs/API.md)
- [에이전트 작업 규칙](AGENTS.md)

## 구현 범위 (프로토타입 v0.1)
- 회원가입·로그인(JWT)
- ISBN 도서 조회
- 오프라인 우선 기록 동기화(멱등 PUT, snapshot)
- 공개 감상 피드·좋아요
- 주간 독서왕 랭킹
