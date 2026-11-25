import { API_BASE_URL, authFetch } from './client'
import type { TagResponse } from './recipe'

// 백엔드 TagController(/api/tags), IngredientController(/api/ingredients/autocomplete),
// OnboardingController(/api/users/me/onboarding)와 같은 모양.
// shortcut: 손으로 맞춘 타입과 fetch 호출이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) api 클라이언트로 바꾼다.

export interface Ingredient {
  id: number
  name: string
}

export interface OnboardingRequest {
  tagIds: number[]
  allergyIngredientIds: number[]
  dislikeIngredientIds: number[]
}

async function parse<T>(res: Response): Promise<T> {
  const data = await res.json().catch(() => null)
  // 백엔드 ErrorResponse의 message를 그대로 보여준다
  if (!res.ok) throw new Error(data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return data as T
}

export async function getTags() {
  return parse<TagResponse[]>(await fetch(`${API_BASE_URL}/api/tags`))
}

export async function autocompleteIngredients(q: string, signal?: AbortSignal) {
  const url = `${API_BASE_URL}/api/ingredients/autocomplete?q=${encodeURIComponent(q)}`
  return parse<Ingredient[]>(await fetch(url, { signal }))
}

export async function saveOnboarding(request: OnboardingRequest) {
  const res = await authFetch(
    new Request(`${API_BASE_URL}/api/users/me/onboarding`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
  )
  if (!res.ok) await parse(res)
}
