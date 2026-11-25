import type { RecipeSummary } from '../api/recipe'
import { listPersonalized, listRecipes } from '../api/recipeList'
import { ApiError, type CursorPage } from '../api/search'

export type SectionKey = 'popular' | 'personalized' | 'ai' | 'latest'

interface Section {
  title: string
  desc: string
  /** 태그를 지원하지 않는 섹션(내 취향 추천)은 tag를 무시한다 */
  load: (tag: number[], cursor: string | undefined, size: number) => Promise<CursorPage<RecipeSummary>>
}

/** 메인 화면 섹션 순서(인기 → 내 취향 추천 → AI → 새 레시피)와 "전체 보기" 목록이 같이 쓴다. */
export const SECTIONS: Record<SectionKey, Section> = {
  popular: {
    title: '인기 레시피',
    desc: '이번 주 좋아요를 많이 받은 레시피',
    load: (tag, cursor, size) => listRecipes({ tag, sort: 'POPULAR', days: 7, cursor, size }),
  },
  personalized: {
    title: '내 취향 추천',
    desc: '좋아요한 레시피와 비슷한 맛',
    load: (_tag, cursor, size) =>
      listPersonalized(cursor, size).then((page) => ({ ...page, items: page.items.map((i) => i.recipe) })),
  },
  ai: {
    title: 'AI 추천 레시피',
    desc: '가진 재료로 AI가 만든 레시피',
    load: (tag, cursor, size) => listRecipes({ tag, source: 'AI_GENERATED', cursor, size }),
  },
  latest: {
    title: '새 레시피',
    desc: '방금 올라온 커뮤니티 레시피',
    load: (tag, cursor, size) => listRecipes({ tag, source: 'COMMUNITY', cursor, size }),
  },
}

export const SECTION_KEYS = Object.keys(SECTIONS) as SectionKey[]

export const isSectionKey = (value: string | null): value is SectionKey =>
  value !== null && SECTION_KEYS.includes(value as SectionKey)

export const sectionErrorMessage = (error: unknown) =>
  error instanceof ApiError && error.status === 401
    ? '로그인하면 취향에 맞는 레시피를 추천해 드려요.'
    : error instanceof Error
      ? error.message
      : '레시피를 불러오지 못했어요.'

/** 쿼리스트링 값 중 양의 정수 ID만 중복 없이 쓴다 */
export const parseIds = (values: string[]) => [
  ...new Set(values.map(Number).filter((n) => Number.isSafeInteger(n) && n > 0)),
]

/** "전체 보기" 주소. 인기는 같은 조건(최근 7일 인기순)의 둘러보기 화면으로 보낸다. */
export function sectionHref(key: SectionKey, tagIds: number[]) {
  if (key === 'popular') return '/browse'
  const query = new URLSearchParams({ section: key })
  tagIds.forEach((id) => query.append('tag', String(id)))
  return `/recipes?${query}`
}
