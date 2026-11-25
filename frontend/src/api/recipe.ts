// 백엔드 RecipeSummaryResponse(목록 항목)와 같은 모양.
// shortcut: 손으로 맞춘 타입이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) components['schemas']로 바꾼다.

export type RecipeSource = 'COMMUNITY' | 'AI_GENERATED'
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
