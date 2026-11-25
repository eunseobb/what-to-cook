import type { Client } from 'openapi-fetch'
import { api } from './client'
import type { RecipeSource, RecipeSummary } from './recipe'
import { ApiError, type CursorPage, type ErrorResponse } from './search'

// 메인 화면 섹션과 "전체 보기" 목록이 쓰는 레시피 목록 API.
// shortcut: 손으로 맞춘 타입과 경로다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) paths·components['schemas']로 바꾼다.

export interface RecipeListQuery {
  tag?: number[]
  sort?: 'LATEST' | 'POPULAR'
  days?: number
  source?: RecipeSource
  cursor?: string
  size?: number
}

export interface PersonalizedRecipe {
  recipe: RecipeSummary
  score: number
}

type Get<Query, Result> = {
  get: {
    parameters: { query: Query }
    responses: {
      200: { headers: Record<string, unknown>; content: { 'application/json': Result } }
      default: { headers: Record<string, unknown>; content: { 'application/json': ErrorResponse } }
    }
  }
}

interface ListPaths {
  '/api/recipes': Get<RecipeListQuery, CursorPage<RecipeSummary>>
  '/api/recommendations/personalized': Get<{ cursor?: string; size?: number }, CursorPage<PersonalizedRecipe>>
}

const client = api as unknown as Client<ListPaths>

async function unwrap<T>(
  call: Promise<{ data?: T; error?: ErrorResponse; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call
  if (data === undefined) throw new ApiError(response.status, error?.message ?? '요청에 실패했어요')
  return data
}

/** 레시피 목록 (F5-9). 로그인하면 알레르기 재료가 든 레시피는 빠진다. */
export const listRecipes = (query: RecipeListQuery) =>
  unwrap(client.GET('/api/recipes', { params: { query } }))

/** 내 취향 추천 (F2-4). 로그인이 필요하다(401). */
export const listPersonalized = (cursor?: string, size?: number) =>
  unwrap(client.GET('/api/recommendations/personalized', { params: { query: { cursor, size } } }))
