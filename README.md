# what-to-cook

가진 재료로 만들 수 있는 레시피를 추천하는 서비스 (가칭: 냉장고 레시피)

## 구조

```
.
├── backend/              # Spring Boot REST API (Java 17, Gradle)
├── docker-compose.yml    # 로컬 개발용 PostgreSQL, Redis
└── .env.example          # 로컬 환경변수 예시
```

## 백엔드 로컬 실행

**필요한 것**: JDK 17, Docker

```bash
cp .env.example .env
docker compose up -d
cd backend && ./gradlew bootRun
```

- 기본 프로파일은 `local`이다. 프로파일은 `local`, `test`, `prod`로 나뉜다.
- Gradle 8.14는 JDK 17~24에서 실행된다. 기본 JDK가 그보다 높으면 `JAVA_HOME`을 17로 지정한다.
- 5432 포트를 이미 쓰고 있다면(로컬에 설치된 PostgreSQL 등) `.env`와 셸에서 `DB_PORT`를 다른 값(예: 5433)으로 바꾼다.

| 주소 | 설명 |
|---|---|
| http://localhost:8080/swagger-ui.html | API 문서 (local에서만 노출) |
| http://localhost:8080/actuator/health | 헬스체크 |
| http://localhost:8080/actuator/prometheus | 메트릭 |

## 테스트

```bash
cd backend && ./gradlew test
```

통합 테스트는 Testcontainers로 PostgreSQL과 Redis를 띄운다. Docker가 없으면 통합 테스트는 건너뛴다.
