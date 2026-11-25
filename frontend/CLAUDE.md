# 프론트엔드 지침: Figma 시안 구현 규칙

루트 `CLAUDE.md` §9(UI/디자인 규칙)를 따르고, 이 문서는 그 규칙을 코드로 옮기는 방법을 정한다. 두 문서가 다르면 루트 문서가 우선한다.

## 1. 디자인 토큰
- 위치: `src/styles/tokens.css`. 일반 CSS 변수만 쓰고, 토큰 변환 도구(Style Dictionary 등)는 두지 않는다. `src/index.css`가 `@import './styles/tokens.css'`로 불러온다.
- 토큰 종류
  - 색상: `--color-*` (값은 루트 §9 표), 오류·경고용 `--color-danger`, `--color-danger-soft`
  - 폰트: `--font-family-base`
  - 모서리: `--radius-card`(카드 썸네일), `--radius-search`(재료 검색창), `--radius-control`(필터 칩, 버튼, 입력창), `--radius-tag`(카드 안 대표 태그), `--radius-pill`(배지)
  - 간격: `--space-1`(4px) ~ `--space-16`(64px), 4px 배수
- 다크모드는 `[data-theme="dark"]`에서 같은 토큰 이름을 재정의한다.

```css
:root {
  --color-canvas: #fffaf0;
  --font-family-base: "Pretendard Variable", Pretendard, -apple-system, sans-serif;
  --radius-card: 16px;
  --space-4: 16px;
}
[data-theme="dark"] { color-scheme: dark; }
```

### Figma 값을 코드로 옮기는 규칙
- 색상 hex 값은 가장 가까운 `--color-*` 토큰으로 바꾼다. 맞는 토큰이 없으면 기존 토큰을 `color-mix()`로 섞어 근사하고, 이유를 주석 한 줄로 남긴다.
  ```css
  /* 시안의 칩 배경(#eef1e1)은 토큰에 없어 sage를 canvas에 섞어 근사한다 */
  background: color-mix(in srgb, var(--color-accent-sage) 30%, var(--color-canvas));
  ```
- 흰색 글씨는 `#fff` 대신 `var(--color-canvas)`를 쓴다.
- 모서리는 쓰임에 맞는 `--radius-*`, 간격은 `--space-*`를 쓴다. 시안 값이 4px 배수가 아닐 때만 px를 그대로 쓴다.
- 글자 크기·굵기 토큰은 없다. 시안의 px 값을 그대로 쓴다.
- 시안 폰트(Noto Sans KR)는 임시다. 구현은 `var(--font-family-base)`(Pretendard)로 한다.
- 대비: `--color-primary` 배경 위 글씨는 18.66px 이상 굵은 글씨에만 쓴다. 작은 녹색 글씨와 링크는 `--color-primary-strong`을 쓴다.

## 2. 프레임워크와 빌드
- React 19, TypeScript(strict, `verbatimModuleSyntax`라서 타입은 `import type`으로 가져온다), Vite.
- 라우팅은 `react-router`(`BrowserRouter`, `NavLink`, `Outlet`). 필터 상태는 쿼리스트링에 둔다.
- 스타일 라이브러리(Tailwind, CSS-in-JS)는 쓰지 않는다. 일반 CSS만 쓴다.
- import 경로 alias는 없다. 상대 경로를 쓴다.
- 린트 `npm run lint`(oxlint), 빌드 `npm run build`(`tsc -b && vite build`).

## 3. 컴포넌트
- 함수형 컴포넌트, named export. 레이아웃 컴포넌트만 default export다.
- props 타입은 인라인으로 쓰거나 `interface XxxProps`로 둔다.
- 위치는 도메인 기준으로 나눈다.
  ```
  src/api/                 # client.ts(openapi-fetch), schema.d.ts(생성 파일), <도메인>.ts 타입
  src/styles/tokens.css
  src/layout/              # 앱 껍데기(헤더, Outlet, 하단 탭바)
  src/components/<도메인>/ # 도메인 컴포넌트와 도메인 CSS 한 파일
  ```
- 새로 만들기 전에 기존 컴포넌트를 먼저 쓴다(예: 레시피 목록은 `RecipeGrid`가 로딩 스켈레톤과 빈 상태까지 처리한다).
- API 호출은 `src/api/client.ts`의 `api`만 쓴다. 인증은 `Authorization: Bearer` 헤더이며 쿠키를 쓰지 않는다.
- API 타입은 `npm run gen:api`로 생성한 `schema.d.ts`의 `components['schemas']`를 쓴다. 생성 전이라 손으로 맞춘 타입에는 `shortcut:` 주석을 단다.
- Storybook과 Code Connect 매핑은 없다.

## 4. 스타일
- 컴포넌트 옆의 일반 CSS 파일을 import하고, 클래스 이름은 BEM 형태(`block__element--modifier`)로 짓는다. CSS Modules는 쓰지 않는다.
  - 예: `.recipe-card__thumb`, `.recipe-card--skeleton`, `.tab-bar__item.active`(NavLink 활성 클래스)
- 전역 스타일은 `src/index.css`에만 둔다(box-sizing 초기화, `body` 배경·글씨색, `h1`~`h4` 색, `main` 최대 폭과 safe-area 패딩).
- 인라인 `style`은 런타임 값(서버가 준 색, 스켈레톤 너비)에만 쓴다.
- 반응형은 화면 폭이 아니라 비율로 나눈다. 데스크톱과 모바일 시안을 컴포넌트 하나와 미디어 쿼리 하나로 구현한다.
  ```css
  @media (max-aspect-ratio: 3/4) { /* 모바일: 헤더 숨김, 하단 탭바, 그리드 2열 */ }
  ```
  - 다열 그리드: `grid-template-columns: repeat(auto-fill, minmax(240px, 1fr))` (1200px 폭에서 4열), 모바일은 `repeat(2, minmax(0, 1fr))`.
  - 하단 고정 요소와 `main` 하단 패딩에 `env(safe-area-inset-bottom)`을 넣는다.
- 접근성
  - `:focus-visible`에 `outline: 2px solid var(--color-primary-strong)`.
  - 애니메이션은 `@media (prefers-reduced-motion: reduce)`에서 끈다.
  - 로딩 목록에는 `aria-busy="true"`, 장식용 요소에는 `aria-hidden="true"`.

## 5. 에셋
- 레시피 이미지는 서버가 준 URL(`mainImageUrl` 등)을 그대로 쓴다. 업로드는 S3 Presigned URL로 하며, 공개 주소(버킷 또는 CDN)는 백엔드 `app.storage.public-base-url`이 정한다. 프론트에 CDN 설정은 두지 않는다.
- 이미지: `<img loading="lazy" alt="">`, `object-fit: cover`, 썸네일 비율 4:3. 이미지가 없으면 대표 태그의 `colorKey`(토큰 이름)로 플레이스홀더 색을 칠한다. 서버 값이므로 토큰 이름 모양(`/^[a-z-]+$/`)일 때만 `var(--color-<key>)`로 쓴다.
- Figma의 썸네일 색 박스는 이미지 자리 표시용이다. 에셋으로 내려받지 않는다.
- 정적 파일은 `public/`(`favicon.svg`, `manifest.webmanifest`)에 둔다.
- 폰트는 `index.html`에서 Pretendard 가변 폰트 동적 서브셋 CSS(jsDelivr, 버전 고정)로 불러온다.

## 6. 아이콘
- 아이콘 세트는 아직 정하지 않았다. 지금은 시안의 텍스트 글리프를 쓰고 `shortcut:` 주석을 단다.
- 아이콘은 `<span aria-hidden="true">`로 감싸고 텍스트 라벨을 함께 둔다.
- 아이콘 라이브러리를 추가해야 하면 먼저 사람에게 확인한다.

## 7. Figma → 코드 작업 순서
1. 루트 `CLAUDE.md`의 "참고 시안 (확정)"에 해당 화면이 있는지 확인한다. 없으면 기존 Figma 파일에 페이지를 추가해 시안(데스크톱 1440px, 모바일 390px)을 만들고, 승인받기 전에는 코드를 쓰지 않는다.
2. Figma MCP로 `create_session` 후 `get_design_context`, `get_screenshot`으로 해당 노드를 읽는다.
3. 받은 코드는 참고용이다. Tailwind·인라인 스타일은 BEM CSS로, hex·radius·간격 값은 토큰으로, Noto Sans KR은 Pretendard로 바꾼다.
4. 기존 컴포넌트와 토큰을 먼저 쓰고, 필요한 것만 새로 만든다.
5. 시안과 토큰·규칙이 다르면 구현을 멈추고 사람에게 확인한다.
