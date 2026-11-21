# 프로젝트 지침: 재료 기반 레시피 추천 서비스 (가칭: 냉장고 레시피)

## Git 규칙 (반드시 지킬 것)

### 브랜치 전략 (Git Flow)
- `main`: 배포 가능한 안정 버전. 직접 커밋하거나 푸시하지 않는다.
- `develop`: 다음 배포를 위한 통합 브랜치. 직접 커밋하거나 푸시하지 않는다.
- `feature/*`: 기능 개발 브랜치. **항상 최신 `develop`에서 분기**하고, PR 대상도 `develop`이다.
- `release/*`, `hotfix/*`: 배포 준비와 긴급 수정용. 사용자가 요청할 때만 만든다.

### 기능별 브랜치
- 작업을 시작하기 전에 기능 단위로 새 브랜치를 만든다. 서로 다른 기능을 한 브랜치에 섞지 않는다.
- 이름 형식: `feature/<도메인>-<기능>` (kebab-case, 영어)
  - 예: `feature/auth-jwt-login`, `feature/ingredient-autocomplete`, `feature/recipe-crud`, `feature/ai-recipe-generation`
- 버그 수정은 `fix/<내용>`, 문서는 `docs/<내용>`, 설정·빌드는 `chore/<내용>` 형식을 쓴다. 분기 기준과 PR 대상은 모두 `develop`이다.
- 브랜치 하나는 PR 하나로 끝낸다. PR은 리뷰할 수 있는 크기로 유지한다.

### 선행 기능이 있는 경우
- 노션 기능 명세의 `선행기능`에 적힌 기능의 PR 브랜치에서 분기하고, PR 대상도 그 브랜치로 한다. 이때는 "항상 develop에서 분기" 규칙의 예외다.
- PR 본문 맨 위에 `관련 기능: <기능 ID>`와 선행 PR 링크를 적는다.
- rebase와 머지는 사람이 한다. 선행 브랜치가 바뀌어도 직접 rebase하지 않는다.

### 작업 순서
1. `git checkout develop && git pull`
2. `git checkout -b feature/<도메인>-<기능>`
3. 작업하고 Conventional Commits 형식으로 커밋한다 (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
4. `git push -u origin <브랜치>`
5. `gh pr create --base develop`로 PR을 만들고, 본문에 변경 내용과 테스트 방법을 적는다.

### 금지 사항
- **PR 머지는 절대 하지 않는다.** 머지는 사람이 직접 한다.
- `gh pr merge`, `git merge`, GitHub API를 통한 머지, 강제 푸시를 사용하지 않는다.
- `main`과 `develop`에 직접 커밋하거나 푸시하지 않는다.

## 0. 프로젝트 성격
- 개인 포트폴리오 프로젝트이며, 결과에 따라 실제 운영으로 전환할 수 있다.
- 기능 구현과 함께 **대용량 트래픽 대응 설계와 부하 테스트 결과**를 포트폴리오의 핵심 성과물로 삼는다.
- 운영 전환을 고려해 처음부터 보안, 로깅, 설정 분리, 외부 API 장애 대응을 갖춘다.

## 1. 서비스 개요
가진 재료를 입력하면 **커뮤니티 레시피를 먼저** 보여주고, 사용자가 "기타 추천"을 누르면 **LLM이 생성한 레시피**를 보여준다. 개인 취향에 맞춰 정렬하고, 재료별 예상 금액을 함께 표시한다.

핵심 원칙
1. 사람이 검증한 레시피(커뮤니티)를 우선하고, LLM은 보조로 쓴다.
2. AI가 생성한 레시피는 저장해서 재사용한다. LLM 비용과 응답 지연을 줄이기 위해서다.
3. 예상 금액은 근사치이며, 화면에 "약 ○○원"으로 표시한다.

## 2. 기술 스택
| 영역 | 선택 |
|---|---|
| Backend | Java 17, Spring Boot 3.4.x, Spring Data JPA (+ QueryDSL), Spring Security + JWT (Access/Refresh) |
| LLM | OpenAI API, Spring AI 사용, Structured Output(JSON Schema)으로 응답 고정 |
| DB | PostgreSQL (또는 MySQL 8), 운영 전환 시 Read Replica 고려 |
| Cache | Redis (인기 레시피, 재료 자동완성, 가격, 좋아요 카운트, Rate Limit) |
| Storage | S3 호환 스토리지 (레시피 이미지, Presigned URL 업로드) |
| Batch | Spring Batch 또는 Scheduler (가격 수집, 인기도 집계) |
| Frontend | React (Vite + TypeScript), 모바일 대응: 반응형 + PWA, 추후 React Native 또는 Capacitor로 앱 전환 가능하게 설계 |
| Infra | Docker Compose (로컬), AWS 배포 (EC2/ECS, RDS, ElastiCache), GitHub Actions CI/CD |
| 관측성 | Spring Actuator, Prometheus, Grafana, 구조화 로그 |
| 부하 테스트 | k6 또는 nGrinder |

### 프론트와 백엔드 계약
- 백엔드는 **순수 REST API 서버**로 만든다. 서버 렌더링은 하지 않는다. 웹과 앱이 같은 API를 쓰게 하기 위해서다.
- API 문서는 springdoc-openapi(Swagger)로 만든다.
- 인증은 쿠키에 의존하지 않고 토큰 방식으로 한다. 앱 전환을 고려한 선택이다.
- 목록 API는 **커서 기반 페이지네이션**으로 만든다.

## 3. 기능 명세

### F1. 재료 입력 (MVP: 텍스트만)
- 자동완성 검색을 제공한다. Redis에서 접두어로 검색한다.
- 여러 재료를 칩 형태로 입력한다.
- **재료 정규화**: 별칭("파", "대파")을 표준 재료(`Ingredient`)에 매핑한다. 매칭되지 않은 입력은 로그로 남겨 마스터 데이터 보강에 쓴다.
- 이미지 인식은 MVP 이후로 미룬다. 그때 OpenAI Vision을 쓰고, 인식 결과는 사용자가 확인하고 수정할 수 있게 한다.

### F2. 레시피 추천
1. **커뮤니티 레시피 우선**
   - 재료 일치율 = 보유 재료 수 ÷ 필요 재료 수. 기본 양념(`isPantryStaple`)은 계산에서 뺀다.
   - 정렬 점수 = `일치율 × w1 + 취향 점수 × w2 + 인기도 × w3`. 가중치는 설정 파일로 뺀다.
   - 알레르기와 비선호 재료가 들어간 레시피는 **하드 필터로 제외**한다.
2. **기타 추천 (LLM)**
   - 요청 재료 집합을 정규화하고 정렬한 뒤 해시를 만들어 `ingredient_set_hash`로 쓴다.
   - 같은 해시로 저장된 AI 레시피가 있으면 그것을 재사용한다(캐시 히트).
   - 없으면 OpenAI를 호출해 JSON 스키마로 1~3개를 생성하고, 검증을 거쳐 `source=AI_GENERATED`로 저장한다.
   - 생성된 레시피는 "AI 생성" 배지로 구분한다. 좋아요를 받거나 저장된 AI 레시피는 일반 추천 후보에도 넣는다.
   - 사용자는 AI 레시피를 편집해서 커뮤니티에 올릴 수 있다.
   - 장애 대응: 타임아웃, 재시도, 서킷 브레이커(Resilience4j), 사용자별 호출 Rate Limit을 둔다.
   - 응답은 비동기로 처리하는 것을 고려한다. 생성 요청을 보내면 결과를 폴링이나 SSE로 받는다.

### F3. 취향 기반 추천
- **온보딩**: 선호 태그(음식 종류, 맛, 목적, 난이도)와 알레르기·비선호 재료를 받는다.
- **행동 반영**: 좋아요(+3), 저장(+2), 상세 조회(+0.5), 업로드(+3)만큼 해당 레시피 태그의 가중치를 올린다. 시간에 따라 감쇠시킨다.
- 취향 점수 = 사용자 태그 벡터와 레시피 태그의 가중합.
- 행동 이벤트는 비동기로 처리한다. 처음에는 Spring Event로 시작하고, 확장할 때 메시지 큐로 바꾼다.

### F4. 재료별 예상 금액
- **1순위 출처: 공공 API (KAMIS 농산물유통정보).** 소매가를 일 단위 배치로 수집해 `IngredientPrice`에 캐시한다.
- **보조 출처: 웹 수집.** 대상 사이트의 이용약관과 robots.txt를 지킬 때만 쓰고, 수집 주기를 제한하며, 출처와 기준일을 저장한다. 수집에 실패하면 마지막 값을 그대로 쓴다.
- 데이터가 없는 재료(가공식품 등)는 카테고리 평균값이나 수동 시드 데이터를 쓴다.
- **단위 환산**: 재료 마스터에 환산 정보를 둔다(1대 = 약 100g 같은 식). 레시피 분량을 가격 단위로 환산해 계산한다.
- 화면에는 "전체 재료비"와 "부족한 재료 구매비"를 표시하고, 기준일과 출처를 함께 보여준다.

### F5. 커뮤니티
- 레시피 CRUD: 제목, 대표 이미지, 재료(마스터 매핑, 수량, 단위), 단계(텍스트와 이미지), 태그, 난이도, 조리시간, 인분.
- 좋아요는 사용자당 1회만 가능하게 유니크 제약을 둔다. 카운트는 Redis에서 집계한 뒤 주기적으로 DB에 반영한다.
- 댓글은 대댓글 1단계까지 허용하고, 삭제는 소프트 삭제로 한다.
- 신고 기능을 두고, 누적 신고 수가 기준을 넘으면 숨김 처리한다.
- 마이페이지에서 내 레시피, 저장한 레시피, 좋아요한 레시피를 볼 수 있다.

## 4. 도메인 모델 (초안)
- `User`, `UserPreferenceTag(user, tag, weight, updatedAt)`, `UserExcludedIngredient(user, ingredient, type: ALLERGY|DISLIKE)`
- `Ingredient(name, aliases, category, baseUnit, unitConversion, isPantryStaple)`
- `IngredientPrice(ingredient, pricePerBaseUnit, referenceDate, source)`
- `Recipe(author, source: COMMUNITY|AI_GENERATED, ingredientSetHash, title, difficulty, cookTime, servings, likeCount, status)`
- `RecipeIngredient(recipe, ingredient, quantity, unit)`, `RecipeStep(recipe, order, content, imageUrl)`, `Tag`, `RecipeTag`
- `RecipeLike(user, recipe)`: 유니크 제약, `RecipeBookmark`, `Comment(recipe, user, parent, content, deleted)`, `Report`

인덱스 고려 대상: `RecipeIngredient(ingredient_id, recipe_id)`, `Recipe(ingredient_set_hash)`, `Recipe(like_count, created_at)`

## 5. 대용량 트래픽 대응과 부하 테스트 (포트폴리오 핵심)
- **시나리오**: ① 재료 기반 레시피 검색, ② 레시피 상세 조회, ③ 좋아요 동시 요청, ④ 인기 레시피 목록, ⑤ AI 추천의 캐시 히트와 미스
- **측정 지표**: TPS, p95/p99 응답시간, 에러율, DB 커넥션, 캐시 히트율
- **개선 과정을 문서로 남긴다**: 각 단계의 Before/After 수치를 기록한다.
  - 재료 매칭 쿼리 최적화(인덱스, 조인 방식, 후보군 사전 축소)
  - 인기 목록과 상세 조회 캐싱
  - 좋아요 동시성 처리(비관적 락 → Redis 원자 연산 비교)
  - N+1 제거, 커넥션 풀 튜닝
  - LLM 호출 격리: 별도 스레드 풀, 서킷 브레이커, Rate Limit
- 테스트용 대량 더미 데이터 생성 스크립트를 만든다. 레시피 10만 건 이상, 사용자 1만 명 이상 규모로 한다.
- 결과는 `docs/performance/`에 리포트로 남긴다.

## 6. 개발 규칙
- ponytail 플러그인 규칙을 따른다. 단, 이 지침에 명시된 설계 요소(서킷 브레이커, Rate Limit, 캐싱, 동시성 비교 구현, 부하 테스트 등)는 ponytail 판단보다 우선한다.
- 패키지 구조는 도메인 기준으로 나눈다: `user`, `ingredient`, `recipe`, `community`, `recommendation`, `price`, `ai`, `global`(설정, 예외, 보안).
- 계층은 Controller → Service → Repository로 두고, DTO와 Entity는 분리한다. Entity를 API 응답으로 직접 내보내지 않는다.
- 공통 응답과 예외 포맷은 `ErrorCode` enum과 `@RestControllerAdvice`로 처리한다.
- 테스트: 서비스 계층은 단위 테스트, Repository는 Testcontainers 기반 통합 테스트로 작성한다. OpenAI 호출은 Mock으로 대체한다.
- 비밀값(OpenAI 키, DB 비밀번호)은 환경변수나 Secret으로 관리하고 커밋하지 않는다.
- 프로파일은 `local`, `test`, `prod`로 나눈다.

## 7. 로드맵
- **1차 (MVP)**: 인증, 온보딩 태그, 재료 마스터와 자동완성, 커뮤니티 레시피 CRUD, 좋아요와 댓글, 재료 기반 추천, LLM 기타 추천과 재사용
- **2차**: 예상 금액(KAMIS 배치), 행동 기반 취향 가중치, 캐싱, 부하 테스트 1회차
- **3차**: 성능 개선과 부하 테스트 2회차, AWS 배포, 모니터링
- **4차**: 이미지 재료 인식, 장보기 리스트, 앱 전환(PWA → Capacitor/RN)

## 8. 초기 과제와 리스크
- **콜드 스타트**: 시드 레시피 수백 건과 재료 마스터를 미리 준비한다. 공공 레시피 데이터(예: 농식품 관련 공공데이터)를 활용할지 검토한다.
- **LLM 품질**: JSON 스키마 검증, 재료 마스터에 없는 재료 처리, 비현실적인 분량 필터링이 필요하다.
- **가격 정확도**: 근사치임을 명시하고, 단위 환산표의 품질을 관리한다.
