# Java Spring Boot 패키지 구조

사용자의 SeniorON_BE/SeniorON/src/main/java/com/example/senioron 구조를 확인하여,
domain 아래 기능별 controller, service, repository, entity, dto/request, dto/response와
global/config, global/jwt, global/apiPayload 분리 방식을 따랐습니다.

- 루트 패키지: `com.dokpamine`
- `domain.user`: 회원가입, 로그인, 내 정보
- `domain.record`: 책, 독서 세션, 감상, 인용문 동기화
- `domain.catalog`: ISBN 서지정보 조회, 조회처 client, ISBN·장르 util
- `domain.feed`: 공개 감상, 좋아요, 책별 감상 검색, 공용 응답 mapper
- `domain.ranking`: KST 주간 타이머 집계
- `global.config`: Spring Security, JWT·외부 조회 설정
- `global.jwt`: JWT 인증 사용자의 UUID 추출
- `global.apiPayload.exception`: API 예외 및 공용 예외 처리
- `global.apiPayload.response`: 오류 응답 DTO

기록 PUT은 요청과 응답 형식이 동일하므로 `domain.record.dto`를 공용으로 사용합니다.
그 외 요청과 응답은 dto/request 및 dto/response로 나눕니다.
기능별로 필요한 폴더만 생성하며 빈 계층이나 미사용 Service 인터페이스를 만들지 않습니다.

Controller → Service → Repository 순서로 의존하며 트랜잭션은 Service에서 관리합니다.
SeniorON의 API 응답 wrapper, 인증 흐름, 데이터베이스 스키마는 가져오지 않았습니다.
Android 클라이언트와의 호환성을 위해 독파민의 기존 URL·JSON·상태 코드와 Flyway V1 스키마를 유지합니다.

Java 21, Spring Boot 4.1.2, Gradle 9.8.2는 독파민 원본의 버전을 유지합니다.
Kotlin 소스·플러그인·Kotlin 전용 Jackson 모듈을 제거하고 Gradle Groovy DSL로 변경했습니다.
DTO에는 Java record를 사용합니다.
