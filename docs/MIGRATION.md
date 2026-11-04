# Java Spring Boot 전환

기준은 `DOKPAMINE/DOKPAMINE-BE`의 `main` 커밋
`f5f3def1682217ebc1417be094a96931b8a5a339`입니다.

- Kotlin 운영 코드와 테스트를 Java로 전환했습니다.
- Gradle Kotlin DSL을 Groovy DSL로 변경했습니다.
- SeniorON처럼 `domain/{기능}` 아래 Controller, Service, Repository, Entity, DTO를 나누고 공용 설정·인증·예외 처리는 `global`로 분리했습니다.
- 기존 API URL·JSON·상태 코드와 Flyway V1 SQL을 유지했습니다.
- 기록의 `deleted` 생략은 false, `visibility` 생략은 PRIVATE이며 명시적인 null은 거절합니다.
- 기존 Kotlin과 같은 유니코드 공백 처리와 DB 마이크로초 정밀도 비교를 유지했습니다.

실행은 `gradlew.bat bootRun`, 검증은 `gradlew.bat build`를 사용합니다.
외부 도서 조회는 테스트에서 모의 응답을 사용합니다.

전체 폴더 구조는 [패키지 구조](PACKAGE_STRUCTURE.md)를 참고하세요.
