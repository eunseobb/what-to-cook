// 백엔드 RecipeSummaryResponse(목록 항목)와 같은 모양.
// shortcut: 손으로 맞춘 타입이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) components['schemas']로 바꾼다.

import type { Client } from 'openapi-fetch'
import { api } from './client'

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

export interface RecipeListQuery {
  tag?: number[]
  sort?: 'LATEST' | 'POPULAR'
  days?: number
  source?: RecipeSource
  cursor?: string
  size?: number
}

// shortcut: schema.d.ts가 비어 있어 GET /api/recipes 경로 타입을 손으로 둔다. gen:api 후 이 타입과 캐스트를 지운다.
type RecipeListPaths = {
  '/api/recipes': {
    get: {
      parameters: { query?: RecipeListQuery }
      responses: { 200: { content: { 'application/json': CursorPage<RecipeSummary> } } }
    }
  }
}

/** 레시피 목록(커서 페이지). 실패하면 예외를 던진다. */
export async function fetchRecipes(query: RecipeListQuery): Promise<CursorPage<RecipeSummary>> {
  const { data, error } = await (api as unknown as Client<RecipeListPaths>).GET('/api/recipes', {
    params: { query },
  })
  if (error !== undefined || !data) throw new Error('레시피 목록을 불러오지 못했어요')
  return data
}
