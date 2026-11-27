import type { Client } from 'openapi-fetch'
import { api } from './client'
import type { Difficulty, RecipeSource, TagType } from './recipe'
import { ApiError, unwrap, type ErrorResponse } from './search'

// 백엔드 AiRecommendationResponse·RecipeDetailResponse와 같은 모양(카드에 쓰는 필드만).
// shortcut: 손으로 맞춘 타입과 경로다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) paths·components['schemas']로 바꾼다.

export interface AiRecipe {
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
  /** 종류·id 순으로 정렬돼 온다 */
  tags: { id: number; type: TagType; name: string }[]
  createdAt: string
}

interface AiRecommendationResponse {
  jobId: string
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  /** COMPLETED일 때만 온다 */
  recipes?: AiRecipe[]
  /** FAILED일 때만 온다 (AI003 서킷 오픈, AI004 타임아웃, AI005 생성 실패) */
  errorCode?: string
  errorMessage?: string
}

type Responses = {
  200: { headers: Record<string, unknown>; content: { 'application/json': AiRecommendationResponse } }
  202: { headers: Record<string, unknown>; content: { 'application/json': AiRecommendationResponse } }
  default: { headers: Record<string, unknown>; content: { 'application/json': ErrorResponse } }
}

interface AiPaths {
  '/api/recommendations/ai': {
    post: {
      requestBody: { content: { 'application/json': { ingredients: string[] } } }
      responses: Responses
    }
  }
  '/api/recommendations/ai/{jobId}': {
    get: { parameters: { path: { jobId: string } }; responses: Responses }
  }
}

const client = api as unknown as Client<AiPaths>

const POLL_INTERVAL_MS = 2000
// 백엔드 LLM 타임아웃(30s)과 재시도를 넉넉히 덮는 시간. 넘으면 타임아웃(AI004)으로 본다.
const POLL_LIMIT_MS = 120_000

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    const id = setTimeout(resolve, ms)
    signal.addEventListener(
      'abort',
      () => {
        clearTimeout(id)
        reject(signal.reason)
      },
      { once: true },
    )
  })

/**
 * AI 레시피를 요청하고 생성이 끝날 때까지 결과를 폴링한다.
 * 같은 재료로 만든 레시피가 있으면 바로 돌려받는다. 실패하면 ApiError(code = 백엔드 ErrorCode)를 던진다.
 */
export async function recommendWithAi(ingredients: string[], signal: AbortSignal): Promise<AiRecipe[]> {
  let job = await unwrap(client.POST('/api/recommendations/ai', { body: { ingredients }, signal }))
  const deadline = Date.now() + POLL_LIMIT_MS
  while (job.status === 'PENDING') {
    if (Date.now() > deadline) throw new ApiError(504, 'AI 레시피 생성 시간이 초과되었습니다.', 'AI004')
    await sleep(POLL_INTERVAL_MS, signal)
    const { jobId } = job
    job = await unwrap(client.GET('/api/recommendations/ai/{jobId}', { params: { path: { jobId } }, signal }))
  }
  if (job.status === 'FAILED') {
    throw new ApiError(200, job.errorMessage ?? 'AI 레시피를 만들지 못했습니다.', job.errorCode)
  }
  return job.recipes ?? []
}
