import { API_BASE_URL, authFetch } from './client'
import type { Difficulty, RecipeSource, TagResponse } from './recipe'

// 레시피 작성·수정 화면이 쓰는 API: 백엔드 RecipeController, UploadController, TagController, IngredientController와 같은 모양.
// shortcut: 손으로 맞춘 타입과 fetch 호출이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) api 클라이언트로 바꾼다.

export interface IngredientOption {
  id: number
  name: string
}

export interface RecipeDetail {
  id: number
  title: string
  mainImageUrl: string | null
  source: RecipeSource
  difficulty: Difficulty
  cookTimeMinutes: number
  servings: number
  authorId: number | null
  ingredients: { ingredientId: number; name: string; quantity: number | null; unit: string | null }[]
  steps: { order: number; content: string; imageUrl: string | null }[]
  tags: { id: number; type: string; name: string }[]
}

/** 등록 요청. 수정(PATCH)도 같은 모양을 보내 모든 필드를 통째로 바꾼다. mainImageUrl은 수정 시 빈 문자열이면 지운다. */
export interface RecipeWriteRequest {
  title: string
  mainImageUrl: string | null
  difficulty: Difficulty
  cookTimeMinutes: number
  servings: number
  ingredients: { ingredientId: number; quantity: number | null; unit: string | null }[]
  steps: { content: string; imageUrl: string | null }[]
  tagIds: number[]
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await authFetch(
    new Request(`${API_BASE_URL}${path}`, {
      ...init,
      headers: init?.body ? { 'Content-Type': 'application/json' } : undefined,
    }),
  )
  const data = res.status === 204 ? null : await res.json().catch(() => null)
  // 백엔드 ErrorResponse의 message를 그대로 보여준다
  if (!res.ok) throw new Error(data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return data as T
}

export const getRecipe = (recipeId: number) => request<RecipeDetail>(`/api/recipes/${recipeId}`)

export const listTags = () => request<TagResponse[]>('/api/tags')

export const autocompleteIngredients = (q: string, signal?: AbortSignal) =>
  request<IngredientOption[]>(`/api/ingredients/autocomplete?q=${encodeURIComponent(q)}`, { signal })

export async function createRecipe(body: RecipeWriteRequest) {
  const { recipeId } = await request<{ recipeId: number }>('/api/recipes', {
    method: 'POST',
    body: JSON.stringify(body),
  })
  return recipeId
}

export const updateRecipe = (recipeId: number, body: RecipeWriteRequest) =>
  request<null>(`/api/recipes/${recipeId}`, {
    method: 'PATCH',
    body: JSON.stringify({ ...body, mainImageUrl: body.mainImageUrl ?? '' }),
  })

// Presigned URL을 받아 스토리지에 직접 PUT하고, 레시피에 넣을 이미지 주소를 돌려준다.
export async function uploadImage(file: File) {
  const { uploadUrl, imageUrl } = await request<{ uploadUrl: string; imageUrl: string }>(
    '/api/uploads/presigned-url',
    { method: 'POST', body: JSON.stringify({ fileName: file.name, contentType: file.type, fileSize: file.size }) },
  )
  // 스토리지 요청이라 우리 토큰을 붙이지 않는다. Content-Length는 브라우저가 파일 크기로 채운다.
  const res = await fetch(uploadUrl, { method: 'PUT', headers: { 'Content-Type': file.type }, body: file })
  if (!res.ok) throw new Error('사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return imageUrl
}
