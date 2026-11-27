// 백엔드 RecipeSummaryResponse(목록 항목)와 같은 모양.
// shortcut: 손으로 맞춘 타입이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) components['schemas']로 바꾼다.

import type { Client } from 'openapi-fetch'
import { api, throwIfFailed } from './client'

export type RecipeSource ='COMMUNITY' | 'AI_GENERATED'
export type Difficulty = 'EASY' | 'NORMAL' | 'HARD'
export type TagType = 'CUISINE' | 'TASTE' | 'PURPOSE' | 'DIFFICULTY'

export interface TagResponse {
  id: number
  type: TagType
  name: string
  /** 디자인 토큰 이름. accent-sage → --color-accent-sage */
  colorKey: string
}

export interface RecipeSummary {
  id: number
  title: string
  mainImageUrl: string | null
  source: RecipeSource
  difficulty: Difficulty
  cookTimeMinutes: number
  servings: number
  likeCount: number
  authorId: number | null
  authorNickname: string | null
  /** 태그가 없으면 null */
  representativeTag: TagResponse | null
  createdAt: string
}

/** 백엔드 CursorPageResponse */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
  hasNext: boolean
}

/** 백엔드 RecipeDetailResponse */
export interface RecipeDetail {
  id: number
  title: string
  mainImageUrl: string | null
  source: RecipeSource
  difficulty: Difficulty
  cookTimeMinutes: number
  servings: number
  likeCount: number
  authorId: number | null
  authorNickname: string | null
  ingredients: { ingredientId: number; name: string; quantity: number | null; unit: string | null }[]
  /** order는 1부터 */
  steps: { order: number; content: string; imageUrl: string | null }[]
  tags: { id: number; type: TagType; name: string }[]
  createdAt: string
  updatedAt: string
}

// shortcut: schema.d.ts가 비어 있어 경로 타입을 손으로 둔다. gen:api 후 이 타입과 캐스트를 지운다.
type RecipeDetailPaths = {
  '/api/recipes/{recipeId}': {
    get: {
      parameters: { path: { recipeId: number } }
      responses: { 200: { content: { 'application/json': RecipeDetail } } }
    }
  }
}

/** 레시피 상세. 없거나 삭제된 레시피면 ApiError(404)를 던진다. */
export async function fetchRecipeDetail(recipeId: number): Promise<RecipeDetail> {
  const { data, error, response } = await (api as unknown as Client<RecipeDetailPaths>).GET(
    '/api/recipes/{recipeId}',
    { params: { path: { recipeId } } },
  )
  throwIfFailed(response, error, '레시피를 불러오지 못했어요')
  return data!
}
