import { API_BASE_URL, authFetch } from './client'
import type { RecipeSummary, TagResponse } from './recipe'

// 백엔드 MyPageController(/api/me)·OnboardingController와 같은 모양.
// shortcut: 손으로 맞춘 타입과 fetch 호출이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) api 클라이언트로 바꾼다.

export interface CursorPage<T> {
  items: T[]
  /** 마지막 페이지면 null */
  nextCursor: string | null
  hasNext: boolean
}

/** 내 레시피(/api/me/recipes), 저장한 레시피(/api/me/bookmarks) */
export type MyRecipeList = 'recipes' | 'bookmarks'

async function getJson<T>(path: string): Promise<T> {
  const res = await authFetch(new Request(`${API_BASE_URL}${path}`))
  if (!res.ok) throw new Error('목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return res.json()
}

export function fetchMyRecipes(list: MyRecipeList, cursor: string | null, size: number) {
  const query = new URLSearchParams({ size: String(size) })
  if (cursor) query.set('cursor', cursor)
  return getJson<CursorPage<RecipeSummary>>(`/api/me/${list}?${query}`)
}

/** 온보딩에서 고른 선호 태그. 프로필의 취향 요약에 쓴다. */
export async function fetchMyPreferenceTags() {
  const { preferenceTags } = await getJson<{ preferenceTags: TagResponse[] }>('/api/users/me/onboarding')
  return preferenceTags
}
