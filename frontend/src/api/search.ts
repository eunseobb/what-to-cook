import type { Client } from 'openapi-fetch'
import { api } from './client'
import type { RecipeSummary, TagResponse } from './recipe'

// 백엔드 응답과 같은 모양.
// shortcut: 손으로 맞춘 타입과 경로다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) paths·components['schemas']로 바꾼다.

/** 자동완성·ID 조회 결과. 별칭으로 찾아도 표준 재료를 준다. */
export interface IngredientOption {
  id: number
  name: string
}

export interface RecommendedRecipe {
  recipe: RecipeSummary
  /** 재료 일치율 0~1 (기본 양념 제외) */
  matchRate: number
}

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
  hasNext: boolean
}

export interface ErrorResponse {
  code: string
  message: string
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

interface SearchPaths {
  '/api/ingredients/autocomplete': Get<{ q: string }, IngredientOption[]>
  '/api/ingredients': Get<{ id: number[] }, IngredientOption[]>
  '/api/tags': Get<Record<string, never>, TagResponse[]>
  '/api/recommendations': Get<
    { ingredient: number[]; tag?: number[]; cursor?: string },
    CursorPage<RecommendedRecipe>
  >
}

const client = api as unknown as Client<SearchPaths>

/** API 오류. status 401이면 로그인이 필요하다. code는 백엔드 ErrorCode(예: AI006)다. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function unwrap<T>(
  call: Promise<{ data?: T; error?: ErrorResponse; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call
  if (data === undefined) throw new ApiError(response.status, error?.message ?? '요청에 실패했어요', error?.code)
  return data
}

export const autocompleteIngredients = (q: string, signal?: AbortSignal) =>
  unwrap(client.GET('/api/ingredients/autocomplete', { params: { query: { q } }, signal }))

export const getIngredients = (ids: number[]) =>
  unwrap(client.GET('/api/ingredients', { params: { query: { id: ids } } }))

export const getTags = () => unwrap(client.GET('/api/tags', { params: { query: {} } }))

export const recommendByIngredients = (ingredient: number[], tag: number[], cursor?: string) =>
  unwrap(client.GET('/api/recommendations', { params: { query: { ingredient, tag, cursor } } }))
